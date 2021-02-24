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

import java.util.regex.Pattern;

import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.scripting.ScriptingException;
import org.apache.ibatis.type.SimpleTypeRegistry;

/**
 * 文本节点，指那些定义在动态标签里的文本，比如if标签里的
 * @author Clinton Begin
 */
public class TextSqlNode implements SqlNode {
  // 包含占位符的原始文本字符串
  private final String text;
  // 校验实参的正则对象
  private final Pattern injectionFilter;

  public TextSqlNode(String text) {
    this(text, null);
  }
  
  public TextSqlNode(String text, Pattern injectionFilter) {
    this.text = text;
    this.injectionFilter = injectionFilter;
  }

  /**
   * 判断当前文本字符串是否包含${}占位符，各种判断字符串中是否存在占位符${}
   * @return
   */
  public boolean isDynamic() {
    DynamicCheckerTokenParser checker = new DynamicCheckerTokenParser();
    GenericTokenParser parser = createParser(checker);
    parser.parse(text);
    return checker.isDynamic();
  }

  /**
   * 从上下文中获取用户传入的实际参数，并使用实际参数替换对应的占位符
   * 再通过injectionFilter 字段所表示的正则对实参进行校验，最后将处理后的sql字符串添加到上下文中。
   * @param context 上下文，在执行时该对象会持有用户传入的实际动态节点上下文
   * @return
   */
  @Override
  public boolean apply(DynamicContext context) {
    // 创建${}占位符解析器
    GenericTokenParser parser = createParser(new BindingTokenParser(context, injectionFilter));
    // 解析${}占位符，并将解析结果添加到DynamicContext中
    context.appendSql(parser.parse(text));
    return true;
  }
  
  private GenericTokenParser createParser(TokenHandler handler) {
    return new GenericTokenParser("${", "}", handler);
  }

  private static class BindingTokenParser implements TokenHandler {

    private DynamicContext context;
    private Pattern injectionFilter;

    public BindingTokenParser(DynamicContext context, Pattern injectionFilter) {
      this.context = context;
      this.injectionFilter = injectionFilter;
    }

    @Override
    public String handleToken(String content) {
      Object parameter = context.getBindings().get("_parameter");
      if (parameter == null) {
        context.getBindings().put("value", null);
      } else if (SimpleTypeRegistry.isSimpleType(parameter.getClass())) {
        context.getBindings().put("value", parameter);
      }
      // 从上下文中获取实际参数
      Object value = OgnlCache.getValue(content, context.getBindings());
      String srtValue = (value == null ? "" : String.valueOf(value)); // issue #274 return "" instead of "null"
      // 通过正则匹配的方式检查实参是否合法
      checkInjection(srtValue);
      return srtValue;
    }

    private void checkInjection(String value) {
      if (injectionFilter != null && !injectionFilter.matcher(value).matches()) {
        throw new ScriptingException("Invalid input. Please conform to regex" + injectionFilter.pattern());
      }
    }
  }
  
  private static class DynamicCheckerTokenParser implements TokenHandler {

    private boolean isDynamic;

    public DynamicCheckerTokenParser() {
      // Prevent Synthetic Access
    }

    public boolean isDynamic() {
      return isDynamic;
    }

    @Override
    public String handleToken(String content) {
      this.isDynamic = true;
      return null;
    }
  }
  
}