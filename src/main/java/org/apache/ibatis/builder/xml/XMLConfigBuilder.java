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

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;
import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * mybatis-config配置文件建造者，有xml解析器等，封装了XPathParser
 * 该类是指是mybatis-config.xml配置文件的对象
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

  private boolean parsed;
  private final XPathParser parser;
  private String environment;
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  /**
   * 字符流配置文件构造XMLConfigBuilder
   * @param reader
   * @param environment
   * @param props
   */
  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(
            /*
             * 创建xml解析器：XPathParser，该解析器为mybatis自定义解析配置文件的工具类，方便对xml的节点操作
             */
            new XPathParser(reader, true, props, new XMLMapperEntityResolver()),
            environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }


  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {


    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  /**
   * 构造XMLConfigBuilder，初始化对应参数
   * @param parser
   * @param environment
   * @param props
   */
  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    // 在构建基类中设置Configuration全局唯一的对象，也就是配置文件，Configuration -> mybatis配置文件
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    // 设置外部properties配置文件加载到configuration对象的变量variables
    this.configuration.setVariables(props);
    // 文件是否已解析flag
    this.parsed = false;
    // 默认使用哪个数据源环境
    this.environment = environment;
    // xml解析器
    this.parser = parser;
  }

  /**
   * 开始解析解析配置文件
   * @return
   */
  public Configuration parse() {
    // 已解析过，报错，因为配置文件只能被解析一次
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    parsed = true;
    // 开始解析，从mybatis配置文件的configuration跟标签开始，注意/configuration没有带*号，/configuration/*
    // 参数：返回对应的根标签XNode
    parseConfiguration(parser.evalNode("/configuration"));
    // 把解析完后一个完整的Configuration对象返回
    return configuration;
  }

  /**
   * 解析mybatis配置文件中的所有标签
   * 请对照mybatis详细的配置文件 官方配置：https://mybatis.org/mybatis-3/zh/configuration.html
   * @param root
   */
  private void parseConfiguration(XNode root) {
    try {
      //issue #117 read properties first
      /**
       * 加载外部properties配置文件
       * <properties resource="db.properties">
       *    <property name="username" value="root"/>
       *    <property name="password" value="root"/>
       * </properties>
       */
      propertiesElement(root.evalNode("properties"));
      /**
       * <settings/>标签
       * settings决定mybatis运行时行为
       * <settings>
       *     <!--全局全局地开启或关闭配置文件中的所有映射器已经配置的任何缓存，默认为true-->
       *     <setting name="cacheEnabled" value="true"/>
       *     <!--延迟加载的全局开关。当开启时，所有关联对象都会延迟加载。 特定关联关系中可通过设置fetchType属性来覆盖该项的开关状态。默认值为false -->
       *     <setting name="lazyLoadingEnabled" value="false"/>
       *     <!--当开启时，任何方法的调用都会加载该对象的所有属性。否则，每个属性会按需加载,默认值false-->
       *     <setting name="aggressiveLazyLoading" value="false"/>
       *     <!--是否允许单一语句返回多结果集,默认值为true -->
       *     <setting name="multipleResultSetsEnabled" value="true"/>
       *     <!--使用列标签代替列名,默认值为true -->
       *     <setting name="useColumnLabel" value="true"/>
       *     <!--允许 JDBC 支持自动生成主键，需要驱动兼容,默认值为false -->
       *     <setting name="useGeneratedKeys" value="false"/>
       * </settings>
       */
      // 把settings解析为Properties对象
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      /**
       * 加载自定义的VFS实现类
       *    VFS含义是虚拟文件系统；主要是通过程序能够方便读取本地文件系统、FTP文件系统等系统中的文件资源。
       *    暂不研究，如果是本地文件系统
       *    如果是FTP文件系统的文件，其实底层就是用了JDK的URLClassPath用来获取远程class文件
       */
      loadCustomVfs(settings);
      /**
       * 实体类别名标签
       * 一个一个文件定义
       * <typeAliases>
       *    <typeAlias type="demo.mybatis.entity.UserInfo" alias="UserInfo"/>
       * </typeAliases>
       * 包扫描自定义
       * <typeAliases>
       *      <package name="demo.mybatis.entity"/>
       * </typeAliases>
       */
      typeAliasesElement(root.evalNode("typeAliases"));
      /**
       * plugins插件解析
       * MyBatis 允许在已映射语句执行过程中的某一点进行拦截调用，需要实现org.apache.ibatis.plugin.Interceptor
       * 比如著名的分页插件com.github.pagehelper就是这种方式实现，
       * 在之前更老的EJB系统中，我就自己写过分页插件，当然是在网上搜的，改造一下。
       * <plugins>
       *     <plugin interceptor="com.github.pagehelper.PageInterceptor">
       *         <!-- config params as the following -->
       *         <property name="param1" value="value1"/>
       * 	</plugin>
       * </plugins>
       */
      pluginElement(root.evalNode("plugins"));

      /** ================ 不常用，暂不研究 ================ */
      objectFactoryElement(root.evalNode("objectFactory"));
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631

      /**
       * 数据源读取
       */
      environmentsElement(root.evalNode("environments"));
      /**
       * 定义多数据库厂商别名，用于在sql语句的标签上databaseId属性指定该sql语句在那个数据库执行。
       *  <!-- 定义数据库厂商标示 -->
       *  <databaseIdProvider type="DB_VENDOR">
       *      <property name="Oracle" value="oracle"/>
       *      <property name="MySQL" value="mysql"/>
       *      <property name="DB2" value="d2"/>
       *  </databaseIdProvider>
       *  <!-- 执行的sql语句指定在那个厂商的数据库执行 -->
       *  <select id="getAllProduct" resultType="product" databaseId="mysql">
       *      SELECT * FROM product
       *  </select>
       */
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      /**
       * 自定义类型转换器，系统内置的org.apache.ibatis.type包下已经足够我们用了，特殊情况下使用
       *  <!--类型处理器 -->
       *  <typeHandlers>
       *    <package name="com.demo.handlers"/>
       *  </typeHandlers>
       * 或
       *  <typeHandlers>
       *      <!-- 注册自定义handler，说明它作用的jdbcType和javaType -->
       *      <typeHandler jdbcType="VARCHAR" javaType="date" handler="com.daily.handler.MyDateHandler" />
       *  </typeHandlers>
       */
      typeHandlerElement(root.evalNode("typeHandlers"));
      /**
       * 加载mappers标签，对应我们自己写的Mapper.xml文件
       */
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  /**
   * settings标签节点解析
   * @param context
   * @return
   */
  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }

    // 所有子节点setting的name，value键值对
    Properties props = context.getChildrenAsProperties();

    // Check that all settings are known to the configuration class
    // 创建Configuration的元信息
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    // 检查解析出来的属性是否是Configuration所规定的属性，通过hasSetter方式来检测，有一个不是就报错
    for (Object key : props.keySet()) {
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          @SuppressWarnings("unchecked")
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>)Resources.classForName(clazz);
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }

  /**
   * 配置别名标签<typeAliases/>的解析
   * @param parent
   */
  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        // 包扫描解析对应实体类别名
        if ("package".equals(child.getName())) {
          String typeAliasPackage = child.getStringAttribute("name");
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {
          // 配置<typeAlias/>标签的别名设置
          String alias = child.getStringAttribute("alias");
          String type = child.getStringAttribute("type");
          try {
            Class<?> clazz = Resources.classForName(type);
            if (alias == null) {
              // 如果没有设置别名，默认使用类型的SimpleName
              typeAliasRegistry.registerAlias(clazz);
            } else {
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

  /**
   * 插件标签<plugins/>解析
   * @param parent
   * @throws Exception
   */
  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      // 遍历所有plugin子标签
      for (XNode child : parent.getChildren()) {
        // mybatis插件实现类全限定类名
        String interceptor = child.getStringAttribute("interceptor");
        // 插件的所有属性
        Properties properties = child.getChildrenAsProperties();
        // 根据全限定类型获取类对象并实例化
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).newInstance();
        // 设置properties
        interceptorInstance.setProperties(properties);
        // 添加插件实例到configuration的InterceptorChain拦截器连链中
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties properties = context.getChildrenAsProperties();
      ObjectFactory factory = (ObjectFactory) resolveClass(type).newInstance();
      factory.setProperties(properties);
      configuration.setObjectFactory(factory);
    }
  }

  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).newInstance();
      configuration.setObjectWrapperFactory(factory);
    }
  }

  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
       String type = context.getStringAttribute("type");
       ReflectorFactory factory = (ReflectorFactory) resolveClass(type).newInstance();
       configuration.setReflectorFactory(factory);
    }
  }

  /**
   * properties标签节点解析
   * @param context
   * @throws Exception
   */
  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      // 获取所有property标签的name，value键值对
      Properties defaults = context.getChildrenAsProperties();
      // resource属性指定本地文件地址
      String resource = context.getStringAttribute("resource");
      // url属性指定远程文件地址
      String url = context.getStringAttribute("url");
      // properties标签不能同时存在resource和url属性
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }
      if (resource != null) {
        // 加载本地properties文件并解析为Properties，添加到defaults
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        // 加载远程properties文件并解析为Properties，添加到defaults
        defaults.putAll(Resources.getUrlAsProperties(url));
      }
      // 获取configuration自有的Properties
      Properties vars = configuration.getVariables();
      if (vars != null) {
        defaults.putAll(vars);
      }
      // Properties所以变量放入parser和configuration的variables中
      parser.setVariables(defaults);
      configuration.setVariables(defaults);
    }
  }

  /**
   * 设置settings对mybatis的配置环境属性
   * @param props
   * @throws Exception
   */
  private void settingsElement(Properties props) throws Exception {
    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    @SuppressWarnings("unchecked")
    Class<? extends TypeHandler> typeHandler = (Class<? extends TypeHandler>)resolveClass(props.getProperty("defaultEnumTypeHandler"));
    configuration.setDefaultEnumTypeHandler(typeHandler);
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    @SuppressWarnings("unchecked")
    Class<? extends Log> logImpl = (Class<? extends Log>)resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
  }

  /**
   * 解析environments标签
   * 注意：mybatis支持定义多数据源，并不支持多数据源切换使用，之所以可以配置多个，是为了测试和生产环境容易切换
   * 如果想使用多个数据源来回切换，需自己开发为不同的数据源创建不同的sqlSession，参考 https://www.cnblogs.com/chenzhanxun/articles/4654203.html
   * @param context
   * @throws Exception
   */
  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      // 构造时没传入指定的environment，则设置default属性值对应的数据库
      if (environment == null) {
        environment = context.getStringAttribute("default");
      }
      for (XNode child : context.getChildren()) {
        String id = child.getStringAttribute("id");
        // 判断指定的default和当前environment标签的id是否一样
        if (isSpecifiedEnvironment(id)) {
          // 事务管理器工厂
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
          // 数据源工厂
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          // 获取具体的数据源
          DataSource dataSource = dsFactory.getDataSource();
          // Environment建造者
          Environment.Builder environmentBuilder = new Environment.Builder(id)
              .transactionFactory(txFactory)
              .dataSource(dataSource);
          // 构建Environment对象存入configuration中
          configuration.setEnvironment(environmentBuilder.build());
        }
        // 否则，配置其他的数据源在这里不做初始化
      }
    }
  }

  /**
   * <databaseIdProvider/>标签解析
   * 该标签定义常用的数据库。
   * mybatis可以根据select | insert | update | delete标签定义的databaseId来决定sql的兼容性，可以把sql解析为对应厂商的能识别的sql语句
   * 注意：数据库厂商name值是固定的，不能瞎写，具体在mybatis官网查看，也可通过使用DatabaseMetaData来查看
   *    <!--数据库厂商标示 -->
   *    <databaseIdProvider type="DB_VENDOR">
   *         <property name="Oracle" value="oracle"/>
   *         <property name="MySQL" value="mysql"/>
   *         <property name="DB2" value="d2"/>
   *         <property name="PostgreSQL" value="pg"/>
   *     </databaseIdProvider>
   * @param context
   * @throws Exception
   */
  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
      // VENDOR 兼容写法
      if ("VENDOR".equals(type)) {
          type = "DB_VENDOR";
      }
      // 构建DatabaseIdProvider并设置属性
      Properties properties = context.getChildrenAsProperties();
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).newInstance();
      databaseIdProvider.setProperties(properties);
    }
    // 获取当前使用的数据源
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      // 获取当前数据源使用的那种数据库厂商ID
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      // 配置当前数据源默认使用的厂商标识ID
      configuration.setDatabaseId(databaseId);
    }
  }

  /**
   * 解析事务管理配置标签<transactionManager />
   * Mybatis支持两种类型的事务管理器：
   *    JDBC: 依赖于从数据源得到的连接来管理事务作用域
   *    MANAGED: 这个配置几乎没做什么。它从来不提交或回滚一个连接，而是让容器来管理事务的整个生命周期（比如 JEE 应用服务器的上下文）。
   * @param context
   * @return
   * @throws Exception
   */
  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      // 那种事务管理方式
      String type = context.getStringAttribute("type");
      // 子标签property
      Properties props = context.getChildrenAsProperties();
      // 获取事务管理对应的实现类
      TransactionFactory factory = (TransactionFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  /**
   * dataSource标签解析
   *  UNPOOLED:这个数据源的实现只是每次被请求时打开和关闭连接
   *  POOLED:这种数据源的实现利用“池”的概念将 JDBC 连接对象组织起来，避免了创建新的连接实例时所必需的初始化和认证时间。 这是一种使得并发 Web 应用快速响应请求的流行处理方式
   *  JNDI:这个数据源的实现是为了能在如 EJB 或应用服务器这类容器中使用，容器可以集中或在外部配置数据源，然后放置一个 JNDI 上下文的引用
   * @param context
   * @return
   * @throws Exception
   */
  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      // 数据源类型UNPOOLED（没用过）、POOLED、JNDI（在很老的EJB保险系统中用过，已经忘了）
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      // 获取对应数据源类型的工厂实例
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  /**
   * <typeHandlers/>标签解析
   * @param parent
   * @throws Exception
   */
  private void typeHandlerElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          String typeHandlerPackage = child.getStringAttribute("name");
          // 扫描包下的typeHandler到typeHandlerRegistry容器
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          // 解析typeHandler标签的属性
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          // 解析属性值为对应的类型
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          if (javaTypeClass != null) {
            // javaTypeClass typeHandlerClass
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              // javaTypeClass jdbcType typeHandlerClass
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            // 只有typeHandlerClass
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  /**
   * mappers标签解析，对应我们自己写的mapper映射文件
   * 以下这三点很重要：
   *    1.在注册映射文件时使用<package name="包名">标签时，需要映射文件名和接口名一样，不然会报错。
   *    2.在注册映射文件时使用<mapper class="">mapper标签的class属性时，需要映射文件名和接口名一样，不然会报错。
   *    3.在注册映射文件时使用<mapper resource="org/xx/demo/mapper/xx.xml"/>，不需要映射文件名和接口名一样。
   *      在和spring集成的时候，配置SqlSessionFactoryBean的mapperLocations是mapper.xml文件，所以mapper文件地址和接口可以不在同一包下
   * @param parent
   * @throws Exception
   */
  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          /**
           * 注意：根据包名扫描接口，不是mapper.xml文件
           * <!-- 将包内的映射器接口实现全部注册为映射器 -->
           * <mappers>
           *   <package name="org.mybatis.builder"/>
           * </mappers>
           */
          String mapperPackage = child.getStringAttribute("name");
          configuration.addMappers(mapperPackage);
        } else {
          // <!-- 使用相对于类路径的资源引用 -->
          // <mapper resource="org/mybatis/builder/AuthorMapper.xml"/>
          String resource = child.getStringAttribute("resource");
          // <!-- 使用完全限定资源定位符（URL） -->
          // <mapper url="file:///var/mappers/AuthorMapper.xml"/>
          String url = child.getStringAttribute("url");
          // 使用映射器接口实现类的完全限定类名 -->
          // <mapper class="org.mybatis.builder.AuthorMapper"/>
          String mapperClass = child.getStringAttribute("class");
          if (resource != null && url == null && mapperClass == null) {
            // resource
            // 获取当前线程的一个ErrorContext对象
            ErrorContext.instance().resource(resource);
            InputStream inputStream = Resources.getResourceAsStream(resource);
            // 解析基于路径mapper.xml文件封装为XMLMapperBuilder
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            // 解析mapper.xml文件中所有的标签元素，并各自封装为对象存入configuration中对应的容器中
            mapperParser.parse();
          } else if (resource == null && url != null && mapperClass == null) {
            // url
            ErrorContext.instance().resource(url);
            InputStream inputStream = Resources.getUrlAsStream(url);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url == null && mapperClass != null) {
            // class
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            configuration.addMapper(mapperInterface);
          } else {
            // 三者都没配置或都配置，报错，只能配置三者中的其中一个
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  /**
   * 判断当前使用的数据源的id和给定参数的是否一致
   * @param id
   * @return
   */
  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
