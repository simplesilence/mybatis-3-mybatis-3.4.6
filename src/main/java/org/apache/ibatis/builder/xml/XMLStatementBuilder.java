/**
 *    Copyright 2009-2017 the original author or authors.
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

import java.util.List;
import java.util.Locale;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

/**
 * 对增删改查标签封装为XMLStatementBuilder建造者，就是MappedStatement的建造者
 * @author Clinton Begin
 */
public class XMLStatementBuilder extends BaseBuilder {

  // mapper文件构建者助手
  private final MapperBuilderAssistant builderAssistant;
  // 增删改查标签节点对象
  private final XNode context;
  // 当前session中的databaseId
  private final String requiredDatabaseId;

  public XMLStatementBuilder(Configuration configuration, MapperBuilderAssistant builderAssistant, XNode context) {
    this(configuration, builderAssistant, context, null);
  }

  public XMLStatementBuilder(Configuration configuration, MapperBuilderAssistant builderAssistant, XNode context, String databaseId) {
    super(configuration);
    this.builderAssistant = builderAssistant;
    this.context = context;
    this.requiredDatabaseId = databaseId;
  }

  /**
   * 解析增删改查节点
   */
  public void parseStatementNode() {
    // 获取id和databaseId属性值
    String id = context.getStringAttribute("id");
    String databaseId = context.getStringAttribute("databaseId");

    // 当前标签的databaseId和当前session的databaseId相等，或都为null才算匹配成功
    if (!databaseIdMatchesCurrent(id, databaseId, this.requiredDatabaseId)) {
      return;
    }
    // 解析其他标签
    Integer fetchSize = context.getIntAttribute("fetchSize");
    Integer timeout = context.getIntAttribute("timeout");
    String parameterMap = context.getStringAttribute("parameterMap");
    String parameterType = context.getStringAttribute("parameterType");
    Class<?> parameterTypeClass = resolveClass(parameterType);
    String resultMap = context.getStringAttribute("resultMap");
    String resultType = context.getStringAttribute("resultType");
    // 该标签是动态 SQL 中的插入脚本语言的功能，不常用，暂不研究
    String lang = context.getStringAttribute("lang");
    // 默认使用XMLLanguageDriver
    LanguageDriver langDriver = getLanguageDriver(lang);

    // 返回值映射的java类型Class对象
    Class<?> resultTypeClass = resolveClass(resultType);
    // 不常用，大概知道作用
    String resultSetType = context.getStringAttribute("resultSetType");
    // 当前sql语句的声明类型，默认使用预编译类型StatementType.PREPARED
    StatementType statementType = StatementType.valueOf(context.getStringAttribute("statementType", StatementType.PREPARED.toString()));
    ResultSetType resultSetTypeEnum = resolveResultSetType(resultSetType);

    // 获取标签名
    String nodeName = context.getNode().getNodeName();
    // 把当前标签名转大写后映射对应的SqlCommandType枚举
    SqlCommandType sqlCommandType = SqlCommandType.valueOf(nodeName.toUpperCase(Locale.ENGLISH));
    // 是否是select标签类型
    boolean isSelect = sqlCommandType == SqlCommandType.SELECT;
    // flushCache将其设置为 true 后，只要语句被调用，都会导致本地缓存和二级缓存被清空，默认值：false。
    // 这里设置为!isSelect，意为只要增删改都刷新缓存
    boolean flushCache = context.getBooleanAttribute("flushCache", !isSelect);
    // useCache将其设置为 true 后，将会导致本条语句的结果被二级缓存缓存起来，默认值：对 select 元素为 true。
    boolean useCache = context.getBooleanAttribute("useCache", isSelect);
    // 该属性的使用方法，参考 https://blog.csdn.net/weixin_40240756/article/details/108889127
    boolean resultOrdered = context.getBooleanAttribute("resultOrdered", false);

    // Include Fragments before parsing
    // 解析include标签，include标签用于引用sql标签
    XMLIncludeTransformer includeParser = new XMLIncludeTransformer(configuration, builderAssistant);
    includeParser.applyIncludes(context.getNode());

    // Parse selectKey after includes and remove them.
    // 解析selectKey标签
    processSelectKeyNodes(id, parameterTypeClass, langDriver);
    
    // Parse the SQL (pre: <selectKey> and <include> were parsed and removed)
    // 解析sql，前提是上面selectKey和include已被解析完从dom树中移除
    SqlSource sqlSource = langDriver.createSqlSource(configuration, context, parameterTypeClass);
    String resultSets = context.getStringAttribute("resultSets");

    /*
     * （仅适用于 insert 和 update）指定能够唯一识别对象的属性，
     * MyBatis 会使用 getGeneratedKeys 的返回值或 insert 语句的 selectKey 子元素设置它的值，
     * 默认值：未设置（unset）。如果生成列不止一个，可以用逗号分隔多个属性名称。
     */
    String keyProperty = context.getStringAttribute("keyProperty");
    /*
     * （仅适用于 insert 和 update）设置生成键值在表中的列名，
     * 在某些数据库（像 PostgreSQL）中，当主键列不是表中的第一列的时候，是必须设置的。如果生成列不止一个，可以用逗号分隔多个属性名称。
     */
    String keyColumn = context.getStringAttribute("keyColumn");
    // 对useGeneratedKeys属性的处理
    KeyGenerator keyGenerator;
    String keyStatementId = id + SelectKeyGenerator.SELECT_KEY_SUFFIX;
    keyStatementId = builderAssistant.applyCurrentNamespace(keyStatementId, true);
    if (configuration.hasKeyGenerator(keyStatementId)) {
      keyGenerator = configuration.getKeyGenerator(keyStatementId);
    } else {
      keyGenerator = context.getBooleanAttribute("useGeneratedKeys",
          configuration.isUseGeneratedKeys() && SqlCommandType.INSERT.equals(sqlCommandType))
          ? Jdbc3KeyGenerator.INSTANCE : NoKeyGenerator.INSTANCE;
    }

    // 构建MappedStatement对象并存入configuration的集合中
    builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType,
        fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass,
        resultSetTypeEnum, flushCache, useCache, resultOrdered, 
        keyGenerator, keyProperty, keyColumn, databaseId, langDriver, resultSets);
  }

  private void processSelectKeyNodes(String id, Class<?> parameterTypeClass, LanguageDriver langDriver) {
    List<XNode> selectKeyNodes = context.evalNodes("selectKey");
    if (configuration.getDatabaseId() != null) {
      parseSelectKeyNodes(id, selectKeyNodes, parameterTypeClass, langDriver, configuration.getDatabaseId());
    }
    parseSelectKeyNodes(id, selectKeyNodes, parameterTypeClass, langDriver, null);
    removeSelectKeyNodes(selectKeyNodes);
  }

  private void parseSelectKeyNodes(String parentId, List<XNode> list, Class<?> parameterTypeClass, LanguageDriver langDriver, String skRequiredDatabaseId) {
    for (XNode nodeToHandle : list) {
      String id = parentId + SelectKeyGenerator.SELECT_KEY_SUFFIX;
      String databaseId = nodeToHandle.getStringAttribute("databaseId");
      if (databaseIdMatchesCurrent(id, databaseId, skRequiredDatabaseId)) {
        parseSelectKeyNode(id, nodeToHandle, parameterTypeClass, langDriver, databaseId);
      }
    }
  }

  private void parseSelectKeyNode(String id, XNode nodeToHandle, Class<?> parameterTypeClass, LanguageDriver langDriver, String databaseId) {
    String resultType = nodeToHandle.getStringAttribute("resultType");
    Class<?> resultTypeClass = resolveClass(resultType);
    StatementType statementType = StatementType.valueOf(nodeToHandle.getStringAttribute("statementType", StatementType.PREPARED.toString()));
    String keyProperty = nodeToHandle.getStringAttribute("keyProperty");
    String keyColumn = nodeToHandle.getStringAttribute("keyColumn");
    boolean executeBefore = "BEFORE".equals(nodeToHandle.getStringAttribute("order", "AFTER"));

    //defaults
    boolean useCache = false;
    boolean resultOrdered = false;
    KeyGenerator keyGenerator = NoKeyGenerator.INSTANCE;
    Integer fetchSize = null;
    Integer timeout = null;
    boolean flushCache = false;
    String parameterMap = null;
    String resultMap = null;
    ResultSetType resultSetTypeEnum = null;

    // 解析sql语句，创建SqlSource
    SqlSource sqlSource = langDriver.createSqlSource(configuration, nodeToHandle, parameterTypeClass);
    SqlCommandType sqlCommandType = SqlCommandType.SELECT;

    builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType,
        fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass,
        resultSetTypeEnum, flushCache, useCache, resultOrdered,
        keyGenerator, keyProperty, keyColumn, databaseId, langDriver, null);

    id = builderAssistant.applyCurrentNamespace(id, false);

    MappedStatement keyStatement = configuration.getMappedStatement(id, false);
    configuration.addKeyGenerator(id, new SelectKeyGenerator(keyStatement, executeBefore));
  }

  private void removeSelectKeyNodes(List<XNode> selectKeyNodes) {
    for (XNode nodeToHandle : selectKeyNodes) {
      nodeToHandle.getParent().getNode().removeChild(nodeToHandle.getNode());
    }
  }

  /**
   * 增删改查标签上的databaseId和当前session配置的databaseId是否匹配
   * 参考sql标签解析时的匹配策略，基本一样的。
   * @param id 增删改查标签的id属性值
   * @param databaseId 增删改查标签的databaseId属性值
   * @param requiredDatabaseId 当前configuration数据库实例的databaseId
   * @return
   */
  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    if (requiredDatabaseId != null) {
      // 不相等，就是不匹配
      if (!requiredDatabaseId.equals(databaseId)) {
        return false;
      }
    } else {
      /*
       * 这一步，说明当前session没有databaseId
       */
      // session中没有databaseId，但增删改查标签配置了，自然不匹配
      if (databaseId != null) {
        return false;
      }
      // 当前session的databaseId和标签的databaseId都为空
      // skip this statement if there is a previous one with a not null databaseId
      // 把当前增删改查标签的id值前面拼上当前mapper文件的namespace
      id = builderAssistant.applyCurrentNamespace(id, false);
      // 当前id是否存在，不在未完成的incompleteStatements集合中查找
      if (this.configuration.hasStatement(id, false)) {
        // 存在，获取当前id所对应的MappedStatement对象
        MappedStatement previous = this.configuration.getMappedStatement(id, false); // issue #2
        // 当前增删改查标签配置有databaseId属性，而session中没有，匹配不成功，忽略掉当前节点
        if (previous.getDatabaseId() != null) {
          return false;
        }
      }
    }
    // 只有当当前标签的databaseId和当前所需databaseId相等或两者都为空才算匹配成功
    return true;
  }

  private LanguageDriver getLanguageDriver(String lang) {
    Class<?> langClass = null;
    if (lang != null) {
      langClass = resolveClass(lang);
    }
    return builderAssistant.getLanguageDriver(langClass);
  }

}
