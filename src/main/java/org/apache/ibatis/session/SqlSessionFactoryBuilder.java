/**
 *    Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.session;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;

/**
 * Builds {@link SqlSession} instances.
 * 该类的职责：
 *    构建SqlSession实例
 *    sqlSession是mybatis对jdbc一次连接会话的封装。
 * @author Clinton Begin
 */
public class SqlSessionFactoryBuilder {

  /** ======================================= 字符流加载mybatis配置文件方式 ========================================= */

  public SqlSessionFactory build(Reader reader) {
    return build(reader, null, null);
  }

  public SqlSessionFactory build(Reader reader, String environment) {
    return build(reader, environment, null);
  }

  public SqlSessionFactory build(Reader reader, Properties properties) {
    return build(reader, null, properties);
  }

  /**
   * 处理解析mybatis环境配置文件
   * @param reader mybatis配置文件的字符流
   * @param environment
   *    环境<environments/>标签上default属性的值，指定默认使用哪个数据库实例，对应数据库实例id的值
   *    <environments/>标签可以配置多个数据库实例，比如可以同时配置mysql<environment id="mysql"/>和oracle<environment id="oracle"/>
   * @param properties
   *    <properties/>标签，指定加载外部properties文件
   * @return
   */
  public SqlSessionFactory build(Reader reader, String environment, Properties properties) {
    try {
      // xml配置文件构建实例
      XMLConfigBuilder parser = new XMLConfigBuilder(reader, environment, properties);
      // 利用Configuration构建sqlSessionFactory
      return build(parser.parse());
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error building SqlSession.", e);
    } finally {
      ErrorContext.instance().reset();
      try {
        reader.close();
      } catch (IOException e) {
        // Intentionally ignore. Prefer previous error.
      }
    }
  }

  /** ======================================= 字节流加载mybatis配置文件方式 ========================================= */

  public SqlSessionFactory build(InputStream inputStream) {
    return build(inputStream, null, null);
  }

  public SqlSessionFactory build(InputStream inputStream, String environment) {
    return build(inputStream, environment, null);
  }

  public SqlSessionFactory build(InputStream inputStream, Properties properties) {
    return build(inputStream, null, properties);
  }

  // 同上面字符流，这里加载配置文件字节流
  public SqlSessionFactory build(InputStream inputStream, String environment, Properties properties) {
    try {
      XMLConfigBuilder parser = new XMLConfigBuilder(inputStream, environment, properties);
      return build(parser.parse());
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error building SqlSession.", e);
    } finally {
      ErrorContext.instance().reset();
      try {
        inputStream.close();
      } catch (IOException e) {
        // Intentionally ignore. Prefer previous error.
      }
    }
  }

  /**
   * 通过加载配置文件后，生成Configuration对象，用该对象创建默认sqlSessionFactory
    * @param config
   * @return
   */
  public SqlSessionFactory build(Configuration config) {
    return new DefaultSqlSessionFactory(config);
  }

}
