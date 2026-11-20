/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.web.server.handler;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

import reactor.core.publisher.Mono;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.WebHandler;

/**
 * Default implementation of {@link WebFilterChain}.
 *
 * <p>Each instance of this class represents one link in the chain. The public
 * constructor {@link #DefaultWebFilterChain(WebHandler, List)}
 * initializes the full chain and represents its first link.
 *
 * <p>This class is immutable and thread-safe. It can be created once and
 * re-used to handle request concurrently.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
// SNIPPET_START DefaultWebFilterChain
public class DefaultWebFilterChain implements WebFilterChain {

	private final List<WebFilter> allFilters;

	// 该属性是DispatchHandler
	private final WebHandler handler;

	@Nullable
	private final WebFilter currentFilter;

	@Nullable
	private final DefaultWebFilterChain chain;


	/**
	 * Public constructor with the list of filters and the target handler to use.
	 * @param handler the target handler
	 * @param filters the filters ahead of the handler
	 * @since 5.1
	 */
	public DefaultWebFilterChain(WebHandler handler, List<WebFilter> filters) {
		Assert.notNull(handler, "WebHandler is required");
		this.allFilters = Collections.unmodifiableList(filters);
		this.handler = handler;
		DefaultWebFilterChain chain = initChain(filters, handler);
		this.currentFilter = chain.currentFilter;
		this.chain = chain.chain;
	}

	private static DefaultWebFilterChain initChain(List<WebFilter> filters, WebHandler handler) {
		// 创建默认的过滤器链，封装WebFilter
		DefaultWebFilterChain chain = new DefaultWebFilterChain(filters, handler, null, null);
		ListIterator<? extends WebFilter> iterator = filters.listIterator(filters.size());
		/*
		 * 从最后一个向前遍历，直到找到第一个filter，作为currentFilter。
		 *
		 * 注意：这里用了一种比较绕的方式（好不好还不一定）：
		 * 每个filter都对应了一个DefaultWebFilterChain，
		 * 而该chain中的currentFilter就是下一个filter（有点绕，注意理解），
		 * 第一次执行这个循环其实就是在创建最后一个filter对应的chain，而该chain是在while循环上面创建的，
		 * 可以看到，那个chain的currentFilter是null，即表示过滤器链的结束。
		 */
		while (iterator.hasPrevious()) {
			chain = new DefaultWebFilterChain(filters, handler, iterator.previous(), chain);
		}
		return chain;
	}

	/**
	 * Private constructor to represent one link in the chain.
	 */
	private DefaultWebFilterChain(List<WebFilter> allFilters, WebHandler handler,
			@Nullable WebFilter currentFilter, @Nullable DefaultWebFilterChain chain) {

		this.allFilters = allFilters;
		this.currentFilter = currentFilter;
		this.handler = handler;
		this.chain = chain;
	}

	/**
	 * Public constructor with the list of filters and the target handler to use.
	 * @param handler the target handler
	 * @param filters the filters ahead of the handler
	 * @deprecated as of 5.1 this constructor is deprecated in favor of
	 * {@link #DefaultWebFilterChain(WebHandler, List)}.
	 */
	@Deprecated
	public DefaultWebFilterChain(WebHandler handler, WebFilter... filters) {
		this(handler, Arrays.asList(filters));
	}


	public List<WebFilter> getFilters() {
		return this.allFilters;
	}

	public WebHandler getHandler() {
		return this.handler;
	}


	@Override
	public Mono<Void> filter(ServerWebExchange exchange) {
		/*
		 * 如果过滤器链没被执行完，则执行过滤器。至于什么时候执行完，
		 * 采用了一种比较绕的方式来实现，参考上面的initChain方法。
		 * 否则调用handler的handle方法，其实这里的handler就是核心组件DispatcherHandler
		 */
		return Mono.defer(() ->
				this.currentFilter != null && this.chain != null ?
						// 如果过滤器链还没执行完则执行过滤器
						invokeFilter(this.currentFilter, this.chain, exchange) :
						// 调用DispatcherHandler中的handle方法
						this.handler.handle(exchange));
	}

	private Mono<Void> invokeFilter(WebFilter current, DefaultWebFilterChain chain, ServerWebExchange exchange) {
		String currentName = current.getClass().getName();
		return current.filter(exchange, chain).checkpoint(currentName + " [DefaultWebFilterChain]");
	}

}
// SNIPPET_END DefaultWebFilterChain