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
package org.apache.ibatis.scripting.xmltags;

/**
 * bind标签，用于变量声明，bind 元素允许你在 OGNL 表达式以外创建一个变量，并将其绑定到当前的上下文。
 * 使用：
 *    <select id="selectBlogsLike" resultType="Blog">
 *      <bind name="pattern" value="'%' + _parameter.getTitle() + '%'" />
 *      SELECT * FROM BLOG
 *      WHERE title LIKE #{pattern}
 *    </select>
 *  注意：上面value的写法，_parameter指传入的对象参数，用get()方式取参，这是OGNL写法，需要注意
 * VarDecl全称为Var Declare
 * @author Frank D. Martinez [mnesarco]
 */
public class VarDeclSqlNode implements SqlNode {
  // bind标签name属性
  private final String name;
  // bind标签value属性
  private final String expression;

  public VarDeclSqlNode(String var, String exp) {
    name = var;
    expression = exp;
  }

  @Override
  public boolean apply(DynamicContext context) {
    // 从Ognl缓存中获取值
    final Object value = OgnlCache.getValue(expression, context.getBindings());
    context.bind(name, value);
    return true;
  }

}
