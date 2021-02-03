/**
 *    Copyright 2009-2015 the original author or authors.
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
package org.apache.ibatis.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.executor.result.ResultMapException;
import org.apache.ibatis.session.Configuration;

/**
 * 该类职责：
 *    在statement setString，setInt...设置参数时被调用（setParameter，setNonNullParameter）
 *    在resultSet getString，getInt...从结果集取数据调用（getResult，getNullableResult）
 * @author Clinton Begin
 * @author Simone Tripodi
 */
public abstract class BaseTypeHandler<T> extends TypeReference<T> implements TypeHandler<T> {

  protected Configuration configuration;

  public void setConfiguration(Configuration c) {
    this.configuration = c;
  }

  /** ============================= 抽象出所有类型转换器共用的行为 ============================ */

  @Override
  public void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
    if (parameter == null) {
      if (jdbcType == null) {
        throw new TypeException("JDBC requires that the JdbcType must be specified for all nullable parameters.");
      }
      try {
        ps.setNull(i, jdbcType.TYPE_CODE);
      } catch (SQLException e) {
        throw new TypeException("Error setting null for parameter #" + i + " with JdbcType " + jdbcType + " . " +
                "Try setting a different JdbcType for this parameter or a different jdbcTypeForNull configuration property. " +
                "Cause: " + e, e);
      }
    } else {
      try {
        setNonNullParameter(ps, i, parameter, jdbcType);
      } catch (Exception e) {
        throw new TypeException("Error setting non null for parameter #" + i + " with JdbcType " + jdbcType + " . " +
                "Try setting a different JdbcType for this parameter or a different configuration property. " +
                "Cause: " + e, e);
      }
    }
  }

  @Override
  public T getResult(ResultSet rs, String columnName) throws SQLException {
    T result;
    try {
      result = getNullableResult(rs, columnName);
    } catch (Exception e) {
      throw new ResultMapException("Error attempting to get column '" + columnName + "' from result set.  Cause: " + e, e);
    }
    if (rs.wasNull()) {
      return null;
    } else {
      return result;
    }
  }

  @Override
  public T getResult(ResultSet rs, int columnIndex) throws SQLException {
    T result;
    try {
      result = getNullableResult(rs, columnIndex);
    } catch (Exception e) {
      throw new ResultMapException("Error attempting to get column #" + columnIndex+ " from result set.  Cause: " + e, e);
    }
    if (rs.wasNull()) {
      return null;
    } else {
      return result;
    }
  }

  @Override
  public T getResult(CallableStatement cs, int columnIndex) throws SQLException {
    T result;
    try {
      result = getNullableResult(cs, columnIndex);
    } catch (Exception e) {
      throw new ResultMapException("Error attempting to get column #" + columnIndex+ " from callable statement.  Cause: " + e, e);
    }
    if (cs.wasNull()) {
      return null;
    } else {
      return result;
    }
  }




  /** ============================= 以下为实现自定义类型转换时和内置类型转换器所需要重写的方法 ============================ */

  /**
   * 设置非空参数
   * @param ps statement
   * @param i jdbc中占位符的索引，从1开始
   * @param parameter 要设置的参数类型
   * @param jdbcType JdbcType枚举
   * @throws SQLException
   */
  public abstract void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException;

  // 根据数据库返回的列明获取值
  public abstract T getNullableResult(ResultSet rs, String columnName) throws SQLException;

  // 根据数据库返回的列索引获取值
  public abstract T getNullableResult(ResultSet rs, int columnIndex) throws SQLException;

  // 根据数据库返回的列索引获取存储过程statement
  public abstract T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException;

//  Example：
//  @Override
//  CustomTypeEnum：自定义枚举类型
//  public void setNonNullParameter(PreparedStatement ps, int i,
//                                  CustomTypeEnum parameter, JdbcType jdbcType) throws SQLException {
//    // 获取枚举的code值，并设置到PreparedStatement中
//    ps.setInt(i, parameter.code());
//  }
//  @Override
//  public CustomTypeEnum getNullableResult(
//          ResultSet rs, String columnName) throws SQLException {
//    // 从ResultSet中取code
//    int code = rs.getInt(columnName);
//    // 解析code对应的枚举，并返回
//    return CustomTypeEnum.find(code);
//  }

}
