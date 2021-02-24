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
 * If标签节点
 * @author Clinton Begin
 */
public class IfSqlNode implements SqlNode {
  // 表达式解析器，用于判断"if test='条件'"是否成立
  private final ExpressionEvaluator evaluator;
  // 成立条件
  private final String test;
  // if节点的子节点
  private final SqlNode contents;

  public IfSqlNode(SqlNode contents, String test) {
    this.test = test;
    this.contents = contents;
    this.evaluator = new ExpressionEvaluator();
  }

  /**
   * 如果当前if节点的条件成立，则判断子节点的条件是否成立
   * @param context 上下文，在执行时该对象会持有用户传入的实际动态节点上下文
   * @return
   */
  @Override
  public boolean apply(DynamicContext context) {
    if (evaluator.evaluateBoolean(test, context.getBindings())) {
      // 子节点是否成立不能影响当前节点的判断结果，所以这里没有直接返回
      contents.apply(context);
      return true;
    }
    return false;
  }

}
