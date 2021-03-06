/**
 *    Copyright 2009-2018 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * Mapper xml 文件建造者
 * 该类主要职责是用来解析mapper.xml文件
 * @author Clinton Begin
 */
public class XMLMapperBuilder extends BaseBuilder {

  private final XPathParser parser;
  private final MapperBuilderAssistant builderAssistant;
  // 存放sql标签节点，命名空间.id属性值作为key，标签节点对象作为值，
  // 这里要注意这个取名，fragment，意为碎片，因为sql标签的设计就是为了重用一些sql碎片
  private final Map<String, XNode> sqlFragments;
  // mapper标签的resource或url路径
  private final String resource;

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(reader, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(reader, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(inputStream, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  /**
   * 构造
   * @param inputStream mapper.xml文件流
   * @param configuration configuration
   * @param resource mapper.xml文件classpath路径
   * @param sqlFragments
   */
  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    super(configuration);
    this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
    this.parser = parser;
    this.sqlFragments = sqlFragments;
    this.resource = resource;
  }

  /**
   * mapper.xml文件解析一览
   */
  public void parse() {
    // mapper是否已经解析
    if (!configuration.isResourceLoaded(resource)) {
      // 从根节点<mapper/> 开始解析
      configurationElement(parser.evalNode("/mapper"));
      // 将mapper标签的resource或url属性值放入configuration的loadedResources集合中
      configuration.addLoadedResource(resource);
      // 绑定当前mapper.xml通过namespace
      bindMapperForNamespace();
    }

    // 处理未完成解析的节点（resultMap）
    parsePendingResultMaps();
    // 处理未完成解析的节点（cache-ref）
    parsePendingCacheRefs();
    // 处理未完成解析的节点（增删改查等sql脚本节点）
    parsePendingStatements();
  }

  public XNode getSqlFragment(String refid) {
    return sqlFragments.get(refid);
  }

  /**
   * 解析mapper文件的各个二级节点
   * @param context
   */
  private void configurationElement(XNode context) {
    try {
      // 获取命名空间的值
      String namespace = context.getStringAttribute("namespace");
      if (namespace == null || namespace.equals("")) {
        throw new BuilderException("Mapper's namespace cannot be empty");
      }
      /**
       * 这里注意一个细节，为什么cache-ref要在cache之前解析
       * 我想是因为怕同一个mapper文件的命名空间被多个其他的mapper文件的cache-ref引用，
       * 这样在解析cache-ref的时候，找到了对应的Cache实例，会设置currentCache就会把当前mapper自定义的cache标签设置的缓存实例覆盖掉，
       * 当前文件的cache标签指定的缓存实例优先级高。
       */
      // 设置当前命名空间
      builderAssistant.setCurrentNamespace(namespace);
      // 解析cache-ref标签
      cacheRefElement(context.evalNode("cache-ref"));
      // 解析cache标签
      cacheElement(context.evalNode("cache"));
      // 该标签已废弃，在未来的版本中会移除。
      // 参数映射标签，和resultMap用法差不多，resultMap映射结果集，parameterMap映射参数
      parameterMapElement(context.evalNodes("/mapper/parameterMap"));
      // 解析resultMap标签
      resultMapElements(context.evalNodes("/mapper/resultMap"));
      // 解析sql标签
      sqlElement(context.evalNodes("/mapper/sql"));
      // 解析select|insert|update|delete标签
      buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing Mapper XML. The XML location is '" + resource + "'. Cause: " + e, e);
    }
  }

  /**
   * 重点：解析sql增删改查操作标签
   * @param list
   */
  private void buildStatementFromContext(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      // 用于解析增删改查标签又单独配置了databaseId属性的标签
      buildStatementFromContext(list, configuration.getDatabaseId());
    }

    /*
     * 细节：这里会再次调用一次，为了解析没有配置databaseId属性的增删改查标签
     */
    buildStatementFromContext(list, null);
  }

  /**
   * 构建增删改查的statement对象
   * @param list
   * @param requiredDatabaseId
   */
  private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
    for (XNode context : list) {
      // 对增删改查标签封装为XMLStatementBuilder建造者
      final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
      try {
        // 解析为MappedStatement对象并放入configuration的mappedStatements集合中
        statementParser.parseStatementNode();
      } catch (IncompleteElementException e) {
        configuration.addIncompleteStatement(statementParser);
      }
    }
  }

  private void parsePendingResultMaps() {
    Collection<ResultMapResolver> incompleteResultMaps = configuration.getIncompleteResultMaps();
    synchronized (incompleteResultMaps) {
      Iterator<ResultMapResolver> iter = incompleteResultMaps.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolve();
          iter.remove();
        } catch (IncompleteElementException e) {
          // ResultMap is still missing a resource...
        }
      }
    }
  }

  private void parsePendingCacheRefs() {
    Collection<CacheRefResolver> incompleteCacheRefs = configuration.getIncompleteCacheRefs();
    synchronized (incompleteCacheRefs) {
      Iterator<CacheRefResolver> iter = incompleteCacheRefs.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolveCacheRef();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Cache ref is still missing a resource...
        }
      }
    }
  }

  private void parsePendingStatements() {
    Collection<XMLStatementBuilder> incompleteStatements = configuration.getIncompleteStatements();
    synchronized (incompleteStatements) {
      Iterator<XMLStatementBuilder> iter = incompleteStatements.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().parseStatementNode();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Statement is still missing a resource...
        }
      }
    }
  }

  /**
   * cache-ref标签解析，我称之为引用二级缓存
   * 注意二级缓存是mapper.xml级的，缓存的是结果，所以在另一个mapper文件中涉及到增删改，会清掉当前缓存的select的结果集
   * 该标签配置另一个xml文件的命名空间
   * 表示在另一个xml文件中执行sql时可以共用当前二级缓存
   * 就是说在执行另一个mapper里的sql语句是，也会到到
   * @param context
   */
  private void cacheRefElement(XNode context) {
    if (context != null) {
      // 把当前文件的命名空间和ref引用的mapper文件命名空间映射在一起，存入Configuration的cacheRefMap中
      configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
      // 引用缓存解析器
      CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, context.getStringAttribute("namespace"));
      try {
        cacheRefResolver.resolveCacheRef();
      } catch (IncompleteElementException e) {
        // 这一步比较重要，如果在缓存中没找到当前命名空间所对应的缓存，则添加到incompleteCacheRefs链表中待解析
        configuration.addIncompleteCacheRef(cacheRefResolver);
      }
    }
  }

  /**
   * cache标签
   * 设置此标签表示开启mybatis二级缓存
   * 关于二级缓存的使用和一级缓存的分析，参考 https://www.jianshu.com/p/c553169c5921
   * 和 https://tech.meituan.com/2018/01/19/mybatis-cache.html 两篇文章都是同一个人写的
   * 二级缓存是针对xml文件中定义的语句进行缓存所有select语句结果，如果改文件中的增删改调用，则会刷新缓存
   * @param context
   * @throws Exception
   */
  private void cacheElement(XNode context) throws Exception {
    if (context != null) {
      // 获取cache标签上的各种属性值
      String type = context.getStringAttribute("type", "PERPETUAL");
      // 没有自定义缓存，默认使用PerpetualCache缓存
      Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);
      String eviction = context.getStringAttribute("eviction", "LRU");
      Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);
      Long flushInterval = context.getLongAttribute("flushInterval");
      Integer size = context.getIntAttribute("size");
      boolean readWrite = !context.getBooleanAttribute("readOnly", false);
      boolean blocking = context.getBooleanAttribute("blocking", false);
      // 把子标签转成Properties对象
      Properties props = context.getChildrenAsProperties();
      // 构建新的缓存对象
      builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
    }
  }

  private void parameterMapElement(List<XNode> list) throws Exception {
    for (XNode parameterMapNode : list) {
      String id = parameterMapNode.getStringAttribute("id");
      String type = parameterMapNode.getStringAttribute("type");
      Class<?> parameterClass = resolveClass(type);
      List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
      List<ParameterMapping> parameterMappings = new ArrayList<ParameterMapping>();
      for (XNode parameterNode : parameterNodes) {
        String property = parameterNode.getStringAttribute("property");
        String javaType = parameterNode.getStringAttribute("javaType");
        String jdbcType = parameterNode.getStringAttribute("jdbcType");
        String resultMap = parameterNode.getStringAttribute("resultMap");
        String mode = parameterNode.getStringAttribute("mode");
        String typeHandler = parameterNode.getStringAttribute("typeHandler");
        Integer numericScale = parameterNode.getIntAttribute("numericScale");
        ParameterMode modeEnum = resolveParameterMode(mode);
        Class<?> javaTypeClass = resolveClass(javaType);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        @SuppressWarnings("unchecked")
        Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
        ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
        parameterMappings.add(parameterMapping);
      }
      builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
    }
  }

  /**
   * resultMap解析，再熟悉不过的标签了
   * @param list
   * @throws Exception
   */
  private void resultMapElements(List<XNode> list) throws Exception {
    for (XNode resultMapNode : list) {
      try {
        resultMapElement(resultMapNode);
      } catch (IncompleteElementException e) {
        // ignore, it will be retried
      }
    }
  }

  /**
   * 重载，resultMap标签解析
   */
  private ResultMap resultMapElement(XNode resultMapNode) throws Exception {
    return resultMapElement(resultMapNode, Collections.<ResultMapping> emptyList());
  }
  /**
   * 重载，resultMap标签解析，resultMap的作用：映射POJO类
   * 到这里希望大家能把mapper.xml里面的使用要熟练，可以看官网XML映射文件项
   * 该方法会被后续处理递归调用，因为association、collection、case标签也会嵌套id、result等标签，这里才好理解第二个参数additionalResultMappings
   */
  private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings) throws Exception {
    ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
    // 解析出resultMap的id和type属性，如果是association、collection、case，就用第二种方式getValueBasedIdentifier设置一个特殊id值，type也是一样
    String id = resultMapNode.getStringAttribute("id",
        resultMapNode.getValueBasedIdentifier());
    String type = resultMapNode.getStringAttribute("type",
        resultMapNode.getStringAttribute("ofType",
            resultMapNode.getStringAttribute("resultType",
                resultMapNode.getStringAttribute("javaType"))));
    // extends属性，resultMap标签可以继承另一个resultMap
    String extend = resultMapNode.getStringAttribute("extends");
    // autoMapping是否自动映射属性，如果为true就是字段名和属性名自动映射，忽略大小写
    // 如果有自动映射的属性，我们一般使用resultType属性，不用费劲在resultMap上，
    // 不过有特殊需求，比如有嵌套一对多，多对多映射，autoMapping不会对嵌套的属性进行映射
    Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
    // resultMap的type的Class对象
    Class<?> typeClass = resolveClass(type);
    Discriminator discriminator = null;
    List<ResultMapping> resultMappings = new ArrayList<ResultMapping>();
    resultMappings.addAll(additionalResultMappings);

    // 获取所有子节点
    List<XNode> resultChildren = resultMapNode.getChildren();
    for (XNode resultChild : resultChildren) {
      // constructor标签，会调用对应POJO的构造方法
      if ("constructor".equals(resultChild.getName())) {
        processConstructorElement(resultChild, typeClass, resultMappings);
      } else if ("discriminator".equals(resultChild.getName())) {
        // 相当于java中的switch，具体看官方文档，是对结果集返回的操作
        discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
      } else {
        // 将id标签作为ResultFlag.ID枚举放入flags集合
        List<ResultFlag> flags = new ArrayList<ResultFlag>();
        if ("id".equals(resultChild.getName())) {
          flags.add(ResultFlag.ID);
        }
        // 将构建好的ResultMapping对象放入集合
        resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
      }
    }
    // 构建一个ResultMap对象解析器
    ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
    try {
      // 解析一个ResultMap对象
      return resultMapResolver.resolve();
    } catch (IncompleteElementException  e) {
      configuration.addIncompleteResultMap(resultMapResolver);
      throw e;
    }
  }

  /**
   * constructor标签解析
   * <constructor>
   *    <idArg column="id" javaType="int"/>
   *    <arg column="username" javaType="String"/>
   *    <arg column="age" javaType="_int"/>
   * </constructor>
   * @param resultChild constructor节点对象
   * @param resultType resultMap的type属性值的Class对象
   * @param resultMappings resultMapping即每个resultMap的子标签的集合
   * @throws Exception
   */
  private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    List<XNode> argChildren = resultChild.getChildren();
    for (XNode argChild : argChildren) {
      List<ResultFlag> flags = new ArrayList<ResultFlag>();
      flags.add(ResultFlag.CONSTRUCTOR);
      if ("idArg".equals(argChild.getName())) {
        flags.add(ResultFlag.ID);
      }
      resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
    }
  }

  /**
   * discriminator标签解析
   *   <discriminator javaType="int" column="vehicle_type">
   *     <case value="1" resultMap="carResult"/>
   *     <case value="2" resultMap="truckResult"/>
   *     <case value="3" resultMap="vanResult"/>
   *     <case value="4" resultMap="suvResult"/>
   *   </discriminator>
   * @param context
   * @param resultType
   * @param resultMappings
   * @return
   * @throws Exception
   */
  private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    // 获取discriminator标签的相关属性
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String typeHandler = context.getStringAttribute("typeHandler");
    // 映射的java类型的Class对象
    Class<?> javaTypeClass = resolveClass(javaType);
    // 类型转换器Class对象
    @SuppressWarnings("unchecked")
    Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
    // 对应的数据库类型
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    Map<String, String> discriminatorMap = new HashMap<String, String>();
    // case标签解析
    for (XNode caseChild : context.getChildren()) {
      String value = caseChild.getStringAttribute("value");
      String resultMap = caseChild.getStringAttribute("resultMap", processNestedResultMappings(caseChild, resultMappings));
      discriminatorMap.put(value, resultMap);
    }
    return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
  }

  /**
   * sql标签解析
   * 核心作用：存入sqlFragments集合（命名空间.id属性值作为key，标签节点对象作为值）
   * @param list
   * @throws Exception
   */
  private void sqlElement(List<XNode> list) throws Exception {
    if (configuration.getDatabaseId() != null) {
      // sql标签又单独配置了databaseId属性的标签
      sqlElement(list, configuration.getDatabaseId());
    }
    /*
     * 细节：再次调用，为了解析sql标签没有配置databaseId属性的
     */
    sqlElement(list, null);
  }

  /**
   * 重载sql标签解析
   * @param list
   * @param requiredDatabaseId
   * @throws Exception
   */
  private void sqlElement(List<XNode> list, String requiredDatabaseId) throws Exception {
    for (XNode context : list) {
      // 获取sql标签上的databaseId属性值
      String databaseId = context.getStringAttribute("databaseId");
      // id属性值
      String id = context.getStringAttribute("id");
      // 重写id属性值，前面加上当前mapper文件的命名空间为：命名空间.id值
      id = builderAssistant.applyCurrentNamespace(id, false);
      // 匹配为true，添加到sqlFragments集合
      if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
        sqlFragments.put(id, context);
      }
    }
  }

  /**
   * sql标签设置的databaseId是否匹配当前SQLSession中的databaseId
   * @param id
   * @param databaseId
   * @param requiredDatabaseId
   * @return
   */
  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    if (requiredDatabaseId != null) {
      // sql标签的databaseId和当前session的databaseId不匹配
      if (!requiredDatabaseId.equals(databaseId)) {
        return false;
      }
    } else {
      /*
       * 走到这里，说明当前session中没有databaseId
       */
      // session中没有databaseId，但sql标签配置了，自然不匹配
      if (databaseId != null) {
        return false;
      }
      // skip this fragment if there is a previous one with a not null databaseId
      // 如果该sql标签的id已存在sqlFragments里，重复了
      if (this.sqlFragments.containsKey(id)) {
        XNode context = this.sqlFragments.get(id);
        // 该标签存在databaseId属性，但当前session中没有，返回false，skip忽略当前节点
        if (context.getStringAttribute("databaseId") != null) {
          return false;
        }
      }
    }
    // 只有当当前标签的databaseId和当前所需databaseId相等或两者都为空才算匹配成功
    return true;
  }

  /**
   * 从该标签上下文构建结果映射，
   * 对id、result、association、collection、case，constructor中（idArg，arg）标签
   * @param context
   * @param resultType
   * @param flags
   * @return
   * @throws Exception
   */
  private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) throws Exception {
    String property;
    if (flags.contains(ResultFlag.CONSTRUCTOR)) {
      property = context.getStringAttribute("name");
    } else {
      property = context.getStringAttribute("property");
    }
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String nestedSelect = context.getStringAttribute("select");
    // 该标签的resultMap属性，一般为association，collection、case标签才有，
    // 注意：resultMap属性和select属性只能存在一个，不然没法映射
    String nestedResultMap = context.getStringAttribute("resultMap",
        processNestedResultMappings(context, Collections.<ResultMapping> emptyList()));
    // 设置不能为空的属性名（嵌套标签中使用）
    String notNullColumn = context.getStringAttribute("notNullColumn");
    // 给每一个属性加一个前缀（嵌套标签中使用）
    String columnPrefix = context.getStringAttribute("columnPrefix");
    // 当前属性所使用的类型转换处理器
    String typeHandler = context.getStringAttribute("typeHandler");
    // 一般用在返回多结果集映射，存储过程中使用
    String resultSet = context.getStringAttribute("resultSet");
    // 标识外键的列名（用在association 和 collection标签）
    String foreignColumn = context.getStringAttribute("foreignColumn");
    // 是否延迟映射数据到POJO
    boolean lazy = "lazy".equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));
    // 该字段对应的java类型
    Class<?> javaTypeClass = resolveClass(javaType);
    // 该字段所使用的类型转换处理器
    @SuppressWarnings("unchecked")
    Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
    // 该字段所对应的数据库类型
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    // 构建resultMap对象
    return builderAssistant.buildResultMapping(resultType, property, column,
            javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap, notNullColumn,
            columnPrefix, typeHandlerClass, flags, resultSet, foreignColumn, lazy);
  }

  /**
   * 处理resultMap嵌套的类resultMap标签，比如association、collection、case
   * @param context
   * @param resultMappings
   * @return
   * @throws Exception
   */
  private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings) throws Exception {
    if ("association".equals(context.getName())
        || "collection".equals(context.getName())
        || "case".equals(context.getName())) {
      // 这些标签上不能有select属性，才可递归解析，否则select就会先去处理另一个sql语句，这个后续会处理
      // 该操作是解析association、collection、case下面的子标签
      if (context.getStringAttribute("select") == null) {
        ResultMap resultMap = resultMapElement(context, resultMappings);
        // 这里id为mapper_resultMap[父resultMap的id值]_association[association的property属性值]
        return resultMap.getId();
      }
    }
    return null;
  }

  /**
   * 将当前mapper文件的命名空间以"namespace:mapper接口路径"的形式存入configuration的loadedResources集合中
   * 将当前mapper文件的命名空间的接口Class对象存入configuration的mapperRegistry对象的knownMappers集合中
   */
  private void bindMapperForNamespace() {
    String namespace = builderAssistant.getCurrentNamespace();
    if (namespace != null) {
      Class<?> boundType = null;
      try {
        boundType = Resources.classForName(namespace);
      } catch (ClassNotFoundException e) {
        //ignore, bound type is not required
      }
      if (boundType != null) {
        if (!configuration.hasMapper(boundType)) {
          // Spring may not know the real resource name so we set a flag
          // to prevent loading again this resource from the mapper interface
          // look at MapperAnnotationBuilder#loadXmlResource
          configuration.addLoadedResource("namespace:" + namespace);
          configuration.addMapper(boundType);
        }
      }
    }
  }

}
