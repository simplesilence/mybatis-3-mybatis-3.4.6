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
package org.apache.ibatis.reflection.property;

import java.util.Iterator;

/**
 * 属性分词器
 *    用于解析复杂属性的写法
 * @author Clinton Begin
 */
public class PropertyTokenizer implements Iterator<PropertyTokenizer> {
  // 属性名
  private String name;
  // TODO 存储跟属性名一样
  private final String indexedName;
  // 如果是数组，存储下标
  private String index;
  // 子级属性
  private final String children;

  public PropertyTokenizer(String fullname) {
    // 属性名中是否包含"."，包含就是复合属性，否则就是单属性
    int delim = fullname.indexOf('.');
    if (delim > -1) {
      /*
       * 名字的第一个.之前的值
       * 例如person.id，将person存入name，id存入children
       */
      name = fullname.substring(0, delim);
      // 剩下的值，不包含.
      children = fullname.substring(delim + 1);
    } else {
      name = fullname;
      children = null;
    }
    indexedName = name;
    // 属性名存在[]，比如personList[0]
    delim = name.indexOf('[');
    if (delim > -1) {
      // 把索引存入index
      index = name.substring(delim + 1, name.length() - 1);
      // 索引之前存入name，比如personList
      name = name.substring(0, delim);
    }
  }

  public String getName() {
    return name;
  }

  public String getIndex() {
    return index;
  }

  public String getIndexedName() {
    return indexedName;
  }

  public String getChildren() {
    return children;
  }

  @Override
  public boolean hasNext() {
    return children != null;
  }

  @Override
  public PropertyTokenizer next() {
    return new PropertyTokenizer(children);
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
  }
}
