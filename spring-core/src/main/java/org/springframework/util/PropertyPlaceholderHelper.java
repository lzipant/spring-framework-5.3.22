/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.lang.Nullable;

/**
 * Utility class for working with Strings that have placeholder values in them.
 * A placeholder takes the form {@code ${name}}. Using {@code PropertyPlaceholderHelper}
 * these placeholders can be substituted for user-supplied values.
 *
 * <p>Values for substitution can be supplied using a {@link Properties} instance or
 * using a {@link PlaceholderResolver}.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @since 3.0
 */
public class PropertyPlaceholderHelper {

	private static final Log logger = LogFactory.getLog(PropertyPlaceholderHelper.class);

	// SNIPPET_START fields

	private static final Map<String, String> wellKnownSimplePrefixes = new HashMap<>(4);

	static {
		wellKnownSimplePrefixes.put("}", "{");
		wellKnownSimplePrefixes.put("]", "[");
		wellKnownSimplePrefixes.put(")", "(");
	}


	private final String placeholderPrefix;

	private final String placeholderSuffix;

	private final String simplePrefix;

	// 占位符名称和属性默认值之间的分隔符
	@Nullable
	private final String valueSeparator;

	// 是否忽略不能够解析的占位符
	private final boolean ignoreUnresolvablePlaceholders;

	// SNIPPET_END fields


	/**
	 * Creates a new {@code PropertyPlaceholderHelper} that uses the supplied prefix and suffix.
	 * Unresolvable placeholders are ignored.
	 * @param placeholderPrefix the prefix that denotes the start of a placeholder
	 * @param placeholderSuffix the suffix that denotes the end of a placeholder
	 */
	// SNIPPET_START constructor_1
	public PropertyPlaceholderHelper(String placeholderPrefix, String placeholderSuffix) {
		this(placeholderPrefix, placeholderSuffix, null, true);
	}
	// SNIPPET_END constructor_1

	/**
	 * Creates a new {@code PropertyPlaceholderHelper} that uses the supplied prefix and suffix.
	 * @param placeholderPrefix the prefix that denotes the start of a placeholder
	 * @param placeholderSuffix the suffix that denotes the end of a placeholder
	 * @param valueSeparator the separating character between the placeholder variable
	 * and the associated default value, if any
	 * @param ignoreUnresolvablePlaceholders indicates whether unresolvable placeholders should
	 * be ignored ({@code true}) or cause an exception ({@code false})
	 */
	// SNIPPET_START constructor_2
	public PropertyPlaceholderHelper(String placeholderPrefix, String placeholderSuffix,
			@Nullable String valueSeparator, boolean ignoreUnresolvablePlaceholders) {

		Assert.notNull(placeholderPrefix, "'placeholderPrefix' must not be null");
		Assert.notNull(placeholderSuffix, "'placeholderSuffix' must not be null");
		this.placeholderPrefix = placeholderPrefix;
		this.placeholderSuffix = placeholderSuffix;
		// 默认的占位符后缀是'}'，所以这里获取到的是：'{'
		String simplePrefixForSuffix = wellKnownSimplePrefixes.get(this.placeholderSuffix);
		/*
		 * 默认的占位符前缀是'${'，所以这里的endsWith是满足的。
		 * 另外在下面的findPlaceholderEndIndex函数中，使用simplePrefix确实可以比使用整个前缀要提高性能。
		 *
		 * 这里不用担心前缀中没有使用预配置好的后缀对应的简化前缀，因为这里使用了endsWith来判断了。
		 */
		if (simplePrefixForSuffix != null && this.placeholderPrefix.endsWith(simplePrefixForSuffix)) {
			// 所以这里的simplePrefix是'{'
			this.simplePrefix = simplePrefixForSuffix;
		}
		else {
			this.simplePrefix = this.placeholderPrefix;
		}
		this.valueSeparator = valueSeparator;
		this.ignoreUnresolvablePlaceholders = ignoreUnresolvablePlaceholders;
	}
	// SNIPPET_END constructor_2


	/**
	 * Replaces all placeholders of format {@code ${name}} with the corresponding
	 * property from the supplied {@link Properties}.
	 * @param value the value containing the placeholders to be replaced
	 * @param properties the {@code Properties} to use for replacement
	 * @return the supplied value with placeholders replaced inline
	 */
	// SNIPPET_START replacePlaceholders_1
	public String replacePlaceholders(String value, final Properties properties) {
		Assert.notNull(properties, "'properties' must not be null");
		return replacePlaceholders(value, properties::getProperty);
	}
	// SNIPPET_END replacePlaceholders_1

	/**
	 * Replaces all placeholders of format {@code ${name}} with the value returned
	 * from the supplied {@link PlaceholderResolver}.
	 * @param value the value containing the placeholders to be replaced
	 * @param placeholderResolver the {@code PlaceholderResolver} to use for replacement
	 * @return the supplied value with placeholders replaced inline
	 */
	// SNIPPET_START replacePlaceholders_2
	public String replacePlaceholders(String value, PlaceholderResolver placeholderResolver) {
		Assert.notNull(value, "'value' must not be null");
		// 解析属性
		return parseStringValue(value, placeholderResolver, null);
	}
	// SNIPPET_END replacePlaceholders_2

	// SNIPPET_START parseStringValue
	protected String parseStringValue(
			String value, PlaceholderResolver placeholderResolver, @Nullable Set<String> visitedPlaceholders) {

		// 查找占位符前缀的索引
		int startIndex = value.indexOf(this.placeholderPrefix);
		if (startIndex == -1) { // 不存在占位符
			return value;
		}

		StringBuilder result = new StringBuilder(value);
		while (startIndex != -1) {
			// 查找占位符的结束索引
			int endIndex = findPlaceholderEndIndex(result, startIndex);
			if (endIndex != -1) {
				// 获取占位符（不包含前后缀）
				String placeholder = result.substring(startIndex + this.placeholderPrefix.length(), endIndex);
				String originalPlaceholder = placeholder;
				if (visitedPlaceholders == null) {
					visitedPlaceholders = new HashSet<>(4);
				}

				/*
				 * 判断属性定义中是否存在环形引用，比如：
				 * key: value
				 * aaa: ${bbb}
				 * bbb: ${aaa}
				 * 这个例子中就出现了属性间的环形依赖，Spring会在这里直接报错。
				 * 解析路径：bbb -> ${aaa} -> aaa -> ${bbb} -> bbb，
				 * 这样在第5次（递归）执行该方法时，visitedPlaceholders中已经存在bbb，所以这里添加会失败。
				 */
				if (!visitedPlaceholders.add(originalPlaceholder)) {
					throw new IllegalArgumentException(
							"Circular placeholder reference '" + originalPlaceholder + "' in property definitions");
				}
				// Recursive invocation, parsing placeholders contained in the placeholder key.
				// 递归解析内嵌的占位符
				placeholder = parseStringValue(placeholder, placeholderResolver, visitedPlaceholders);
				// Now obtain the value for the fully resolved key...
				// 解析占位符的值，执行到这里，placeholder是纯粹的属性名称，不包含任何属性占位符标记
				String propVal = placeholderResolver.resolvePlaceholder(placeholder);
				// 获取到值，如果值分割标记不为null，则处理默认值，例如：${port: 8080}，port是属性名称，8080是该属性的默认值
				if (propVal == null && this.valueSeparator != null) {
					// 获取值分割标记的下标
					int separatorIndex = placeholder.indexOf(this.valueSeparator);
					// 存在默认值
					if (separatorIndex != -1) {
						// 解析实际的属性名称
						String actualPlaceholder = placeholder.substring(0, separatorIndex);
						// 解析默认值
						String defaultValue = placeholder.substring(separatorIndex + this.valueSeparator.length());
						/*
						 * 如果是存在默认值的情况，那么上面resolvePlaceholder是获取不到的值，毕竟不会设置一个叫作'port: 8080'的属性，
						 * 所以这里去掉默认值后，以纯粹的属性名称来解析占位符的值。
						 */
						propVal = placeholderResolver.resolvePlaceholder(actualPlaceholder);
						// 如果目标属性还是不存在，则采用默认值
						if (propVal == null) {
							propVal = defaultValue;
						}
					}
				}
				if (propVal != null) {
					// Recursive invocation, parsing placeholders contained in the
					// previously resolved placeholder value.
					// 如果属性值中仍然包含占位符，那么继续递归解析
					propVal = parseStringValue(propVal, placeholderResolver, visitedPlaceholders);
					// 将整个属性占位符替换为实际的属性值
					result.replace(startIndex, endIndex + this.placeholderSuffix.length(), propVal);
					if (logger.isTraceEnabled()) {
						logger.trace("Resolved placeholder '" + placeholder + "'");
					}
					/*
					 * 从替换后的位置开始往后查找第一个占位符前缀的下标，这里确保了在调用findPlaceholderEndIndex函数之前时，
					 * buf参数中是包含占位符前缀的。
					 * 这样就可以避免${outer-${inner}-12}3}（后缀数量大于前缀数量的情况）情况的出现，
					 * 那么在findPlaceholderEndIndex函数中就只需要考虑前缀数量是否大于后缀数量的情况。
					 */
					startIndex = result.indexOf(this.placeholderPrefix, startIndex + propVal.length());
				}
				else if (this.ignoreUnresolvablePlaceholders) { // 忽略不能够解析的占位符
					// Proceed with unprocessed value.
					// 和上一个if分支中的最后一条语句一样
					startIndex = result.indexOf(this.placeholderPrefix, endIndex + this.placeholderSuffix.length());
				}
				else { // 不能忽略不能够解析的占位符
					throw new IllegalArgumentException("Could not resolve placeholder '" +
							placeholder + "'" + " in value \"" + value + "\"");
				}
				visitedPlaceholders.remove(originalPlaceholder);
			}
			else {
				// 停止解析，结束循环
				startIndex = -1;
			}
		}
		return result.toString();
	}
	// SNIPPET_END parseStringValue

	// SNIPPET_START findPlaceholderEndIndex
	/*
	 * 该函数不仅解析了占位符后缀的索引，而且还判断了内嵌的占位符是否合法（也就是判断前缀和后缀的数量是否相等）。
	 * 注意在该函数中没有考虑后缀数量大于前缀数量的情况，比如${outer-${inner}-12}3}，第二个内嵌占位符后缀没有对应的前缀，
	 * 但该函数是会正常返回后缀下标的，但由于在上面parseStringValue方法中是确保了buf中包含占位符前缀才会调用该方法，所以对于这个例子，
	 * 解析第二个后缀占位符时是根本不会进入到该方法中的。
	 */
	private int findPlaceholderEndIndex(CharSequence buf, int startIndex) {
		// 从占位符前缀的下一个字符开始遍历
		int index = startIndex + this.placeholderPrefix.length();
		// 如果占位符是合法的，也就是前缀和后缀数量相等，那么解析到最后，该变量也应该是0
		int withinNestedPlaceholder = 0;
		// 往后一个个字符地匹配
		while (index < buf.length()) {
			// 匹配上占位符后缀
			if (StringUtils.substringMatch(buf, index, this.placeholderSuffix)) {
				// 后缀数量不等于前缀数量
				if (withinNestedPlaceholder > 0) {
					// 利用当前解析到的后缀抵消掉一个前缀
					withinNestedPlaceholder--;
					// 跳过当前解析到的后缀，以便继续往后遍历
					index = index + this.placeholderSuffix.length();
				}
				else { // 正常情况：即当前后缀是和之前解析到的前缀是一一对应的
					return index;
				}
			}
			/*
			 * 解析到了前缀
			 * 为什么这里用simplePrefix（默认值为'{'），而不是placeholderPrefix（默认值为'${'）？
			 * 仅仅是在匹配时只用匹配一个字符（而不是两个）来提高性能？（大概率是了）
			 * 因为前后缀都是可以自定义的，比如定义一个复杂的前缀，例如: '$$$$$$$$$$${'，使用'{'来简化匹配确实可以提高性能。
			 */
			else if (StringUtils.substringMatch(buf, index, this.simplePrefix)) {
				// 标记匹配上了一个前缀
				withinNestedPlaceholder++;
				// 跳过前缀，以便继续往后遍历
				index = index + this.simplePrefix.length();
			}
			// 跳到下一个字符进行匹配
			else {
				index++;
			}
		}
		return -1;
	}
	// SNIPPET_END findPlaceholderEndIndex


	/**
	 * Strategy interface used to resolve replacement values for placeholders contained in Strings.
	 */
	@FunctionalInterface
	public interface PlaceholderResolver {

		/**
		 * Resolve the supplied placeholder name to the replacement value.
		 * @param placeholderName the name of the placeholder to resolve
		 * @return the replacement value, or {@code null} if no replacement is to be made
		 */
		@Nullable
		String resolvePlaceholder(String placeholderName);
	}

}
