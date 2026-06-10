package com.zc.iflyzcragback.service.rag.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CalculatorPlugin 为 LangChain4j 工具调用提供确定性的表达式计算能力。
 *
 * <p>这里刻意只支持一小段白名单算术语法。解析逻辑留在本地且显式可见，
 * 可以避免脚本执行风险，同时覆盖聊天里的常见数字计算问题。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalculatorPlugin implements ManagedTool {

    public static final String NAME = "calculator";

    private static final int MAX_EXPRESSION_LENGTH = 512;

    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String displayName() {
        return "CalculatorPlugin";
    }

    @Override
    public String description() {
        return "计算数学表达式";
    }

    @Tool(name = NAME, value = "计算数学表达式。只接收一个必填参数 expression，支持数字、小数、括号、+、-、*、/、% 和一元正负号。")
    public String calculate(@P("要计算的数学表达式，例如 1 + 2 * (3 - 4)") String expression) {
        try {
            validateExpression(expression);
            BigDecimal result = new Parser(expression).parse();
            String formattedResult = format(result);
            log.info("Calculator tool finished | expression=\"{}\" | result={}", expression, formattedResult);
            return success(expression, formattedResult);
        } catch (Exception e) {
            log.warn("Calculator tool failed | expression=\"{}\" | message={}", expression, e.getMessage());
            return failure(expression, e.getMessage());
        }
    }

    /**
     * 在解析前拒绝所有算术语法外的输入。
     */
    private void validateExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("calculator 缺少 expression 参数");
        }
        if (expression.length() > MAX_EXPRESSION_LENGTH) {
            throw new IllegalArgumentException("表达式长度不能超过 " + MAX_EXPRESSION_LENGTH + " 个字符");
        }
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (!Character.isDigit(c)
                    && !Character.isWhitespace(c)
                    && c != '.'
                    && c != '('
                    && c != ')'
                    && c != '+'
                    && c != '-'
                    && c != '*'
                    && c != '/'
                    && c != '%') {
                throw new IllegalArgumentException("表达式包含不支持的字符: " + c);
            }
        }
    }

    private String success(String expression, String result) {
        return writeJson(Map.of(
                "success", true,
                "expression", expression,
                "result", result,
                "message", "计算完成"
        ));
    }

    private String failure(String expression, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("expression", expression);
        body.put("result", null);
        body.put("message", message == null || message.isBlank() ? "计算失败" : message);
        return writeJson(body);
    }

    private String writeJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"计算工具返回结果序列化失败\"}";
        }
    }

    /**
     * 将面向用户的结果标准化，避免科学计数法和多余的末尾 0。
     */
    private static String format(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    /**
     * 递归下降解析器，支持的语法为：
     * expression = term (("+" | "-") term)*
     * term       = unary (("*" | "/" | "%") unary)*
     * unary      = ("+" | "-") unary | primary
     * primary    = number | "(" expression ")"
     */
    private static class Parser {

        private final String input;
        private int index;

        Parser(String input) {
            this.input = input;
        }

        BigDecimal parse() {
            BigDecimal value = expression();
            skipWhitespace();
            if (index != input.length()) {
                throw new IllegalArgumentException("表达式解析失败，位置: " + index);
            }
            return value;
        }

        /**
         * 解析最低优先级的加减运算。
         */
        private BigDecimal expression() {
            BigDecimal value = term();
            while (true) {
                skipWhitespace();
                if (match('+')) {
                    value = value.add(term(), MathContext.DECIMAL128);
                } else if (match('-')) {
                    value = value.subtract(term(), MathContext.DECIMAL128);
                } else {
                    return value;
                }
            }
        }

        /**
         * 解析更高优先级的乘、除、取余运算。
         */
        private BigDecimal term() {
            BigDecimal value = unary();
            while (true) {
                skipWhitespace();
                if (match('*')) {
                    value = value.multiply(unary(), MathContext.DECIMAL128);
                } else if (match('/')) {
                    BigDecimal divisor = unary();
                    if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                        throw new IllegalArgumentException("除数不能为 0");
                    }
                    value = value.divide(divisor, MathContext.DECIMAL128);
                } else if (match('%')) {
                    BigDecimal divisor = unary();
                    if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                        throw new IllegalArgumentException("除数不能为 0");
                    }
                    value = value.remainder(divisor, MathContext.DECIMAL128);
                } else {
                    return value;
                }
            }
        }

        /**
         * 处理连续一元正负号，例如 --1 或 +(-2)。
         */
        private BigDecimal unary() {
            skipWhitespace();
            if (match('+')) {
                return unary();
            }
            if (match('-')) {
                return unary().negate(MathContext.DECIMAL128);
            }
            return primary();
        }

        /**
         * 解析括号表达式或数字字面量。
         */
        private BigDecimal primary() {
            skipWhitespace();
            if (match('(')) {
                BigDecimal value = expression();
                skipWhitespace();
                if (!match(')')) {
                    throw new IllegalArgumentException("括号不匹配");
                }
                return value;
            }
            return number();
        }

        /**
         * 解析不含指数写法的小数，保持语法边界足够窄。
         */
        private BigDecimal number() {
            skipWhitespace();
            int start = index;
            boolean hasDigit = false;
            boolean hasDot = false;
            while (index < input.length()) {
                char c = input.charAt(index);
                if (Character.isDigit(c)) {
                    hasDigit = true;
                    index++;
                } else if (c == '.' && !hasDot) {
                    hasDot = true;
                    index++;
                } else {
                    break;
                }
            }
            if (!hasDigit) {
                throw new IllegalArgumentException("缺少数字，位置: " + start);
            }
            try {
                return new BigDecimal(input.substring(start, index), MathContext.DECIMAL128);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("数字格式错误，位置: " + start);
            }
        }

        private boolean match(char expected) {
            if (index < input.length() && input.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
                index++;
            }
        }
    }
}
