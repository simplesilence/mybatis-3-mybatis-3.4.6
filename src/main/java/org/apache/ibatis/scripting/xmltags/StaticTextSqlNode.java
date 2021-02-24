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
package org.apache.ibatis.scripting.xmltags;

/**
 * 文本类型sql节点，其中的文本不包含任何动态元素，即不包括${}这种占位符
 * @author Clinton Begin
 */
public class StaticTextSqlNode implements SqlNode {
  private final String text;

  public StaticTextSqlNode(String text) {
    this.text = text;
  }

  /**
   * 因为StaticTextSqlNode表示不包含任何动态元素的节点，所以不需要对其做任何处理
   * @param context 上下文，在执行时该对象会持有用户传入的实际动态节点上下文
   * @return
   */
  @Override
  public boolean apply(DynamicContext context) {
    context.appendSql(text);
    return true;
  }

}