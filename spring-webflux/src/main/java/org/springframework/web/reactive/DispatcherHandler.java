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

package org.springframework.web.reactive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.cors.reactive.PreFlightRequestHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

/**
 * Central dispatcher for HTTP request handlers/controllers. Dispatches to
 * registered handlers for processing a request, providing convenient mapping
 * facilities.
 *
 * <p>{@code DispatcherHandler} discovers the delegate components it needs from
 * Spring configuration. It detects the following in the application context:
 * <ul>
 * <li>{@link HandlerMapping} -- map requests to handler objects
 * <li>{@link HandlerAdapter} -- for using any handler interface
 * <li>{@link HandlerResultHandler} -- process handler return values
 * </ul>
 *
 * <p>{@code DispatcherHandler} is also designed to be a Spring bean itself and
 * implements {@link ApplicationContextAware} for access to the context it runs
 * in. If {@code DispatcherHandler} is declared as a bean with the name
 * "webHandler", it is discovered by
 * {@link WebHttpHandlerBuilder#applicationContext(ApplicationContext)} which
 * puts together a processing chain together with {@code WebFilter},
 * {@code WebExceptionHandler} and others.
 *
 * <p>A {@code DispatcherHandler} bean declaration is included in
 * {@link org.springframework.web.reactive.config.EnableWebFlux @EnableWebFlux}
 * configuration.
 *
 * @author Rossen Stoyanchev
 * @author Sebastien Deleuze
 * @author Juergen Hoeller
 * @since 5.0
 * @see WebHttpHandlerBuilder#applicationContext(ApplicationContext)
 */
public class DispatcherHandler implements WebHandler, PreFlightRequestHandler, ApplicationContextAware {

	@Nullable
	private List<HandlerMapping> handlerMappings;

	@Nullable
	private List<HandlerAdapter> handlerAdapters;

	@Nullable
	private List<HandlerResultHandler> resultHandlers;


	/**
	 * Create a new {@code DispatcherHandler} which needs to be configured with
	 * an {@link ApplicationContext} through {@link #setApplicationContext}.
	 */
	// SNIPPET_START constructor_1
	public DispatcherHandler() {
	}
	// SNIPPET_END constructor_1

	/**
	 * Create a new {@code DispatcherHandler} for the given {@link ApplicationContext}.
	 * @param applicationContext the application context to find the handler beans in
	 */
	// SNIPPET_START constructor_2
	public DispatcherHandler(ApplicationContext applicationContext) {
		initStrategies(applicationContext);
	}
	// SNIPPET_END constructor_2


	/**
	 * Return all {@link HandlerMapping} beans detected by type in the
	 * {@link #setApplicationContext injected context} and also
	 * {@link AnnotationAwareOrderComparator#sort(List) sorted}.
	 * <p><strong>Note:</strong> This method may return {@code null} if invoked
	 * prior to {@link #setApplicationContext(ApplicationContext)}.
	 * @return immutable list with the configured mappings or {@code null}
	 */
	@Nullable
	public final List<HandlerMapping> getHandlerMappings() {
		return this.handlerMappings;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		initStrategies(applicationContext);
	}


	// SNIPPET_START initStrategies
	/*
	 * 这个方法中获取的依赖在WebFluxConfigurationSupport中有注入，
	 * 而@EnableWebFlux注解引入了配置类DelegatingWebFluxConfiguration，这个类就是前者的子类。
	 * 在SpringBoot中可以不用指定@EnableWebFlux，而在WebFluxAutoConfiguration中定义了一个DelegatingWebFluxConfiguration的子类。
	 */
	protected void initStrategies(ApplicationContext context) {
		/*
		 * 从容器上下文中获取HandlerMapping实例
		 * 主要参考：RequestMappingHandlerMapping，注意这是reactive包下的，不是webmvc包下的，两个类同名。
		 */
		Map<String, HandlerMapping> mappingBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(
				context, HandlerMapping.class, true, false);

		ArrayList<HandlerMapping> mappings = new ArrayList<>(mappingBeans.values());
		AnnotationAwareOrderComparator.sort(mappings);
		this.handlerMappings = Collections.unmodifiableList(mappings);

		/*
		 * 从容器上下文中获取HandlerAdapter实例
		 * 主要参考：RequestMappingHandlerAdapter，注意这是reactive包下的，不是webmvc包下的，两个类同名。
		 */
		Map<String, HandlerAdapter> adapterBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(
				context, HandlerAdapter.class, true, false);

		this.handlerAdapters = new ArrayList<>(adapterBeans.values());
		AnnotationAwareOrderComparator.sort(this.handlerAdapters);

		/*
		 * 从容器中获取HandlerResultHandler实例
		 * 主要参考：ResponseBodyResultHandler，这个最常用。
		 */
		Map<String, HandlerResultHandler> beans = BeanFactoryUtils.beansOfTypeIncludingAncestors(
				context, HandlerResultHandler.class, true, false);

		this.resultHandlers = new ArrayList<>(beans.values());
		AnnotationAwareOrderComparator.sort(this.resultHandlers);
	}
	// SNIPPET_END initStrategies


	// SNIPPET_START handle
	@Override
	public Mono<Void> handle(ServerWebExchange exchange) {
		if (this.handlerMappings == null) {
			return createNotFoundError();
		}
		// 处理预检请求
		if (CorsUtils.isPreFlightRequest(exchange.getRequest())) {
			return handlePreFlight(exchange);
		}
		// 遍历所有的handlerMapping
		return Flux.fromIterable(this.handlerMappings)
				// 从HandlerMapping中获取handler
				.concatMap(mapping -> mapping.getHandler(exchange))
				// 因为上面遍历了多个handlerMapping，可能多个mapping会返回handler，这里选取第一个。
				.next()
				// 如果没匹配到handlerMethod，则处理错误
				.switchIfEmpty(createNotFoundError())
				// 执行handler，会由HandlerAdapter处理
				.flatMap(handler -> invokeHandler(exchange, handler))
				.flatMap(result -> handleResult(exchange, result));
	}
	// SNIPPET_END handle

	private <R> Mono<R> createNotFoundError() {
		return Mono.defer(() -> {
			Exception ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "No matching handler");
			return Mono.error(ex);
		});
	}

	private Mono<HandlerResult> invokeHandler(ServerWebExchange exchange, Object handler) {
		if (ObjectUtils.nullSafeEquals(exchange.getResponse().getStatusCode(), HttpStatus.FORBIDDEN)) {
			return Mono.empty();  // CORS rejection
		}
		if (this.handlerAdapters != null) {
			for (HandlerAdapter handlerAdapter : this.handlerAdapters) {
				// 判断所有的HandlerAdapter是否支持该handler，如果支持的话则执行其handle逻辑
				if (handlerAdapter.supports(handler)) {
					return handlerAdapter.handle(exchange, handler);
				}
			}
		}
		return Mono.error(new IllegalStateException("No HandlerAdapter: " + handler));
	}

	private Mono<Void> handleResult(ServerWebExchange exchange, HandlerResult result) {
		return getResultHandler(result).handleResult(exchange, result)
				.checkpoint("Handler " + result.getHandler() + " [DispatcherHandler]")
				.onErrorResume(ex ->
						result.applyExceptionHandler(ex).flatMap(exResult -> {
							String text = "Exception handler " + exResult.getHandler() +
									", error=\"" + ex.getMessage() + "\" [DispatcherHandler]";
							return getResultHandler(exResult).handleResult(exchange, exResult).checkpoint(text);
						}));
	}

	private HandlerResultHandler getResultHandler(HandlerResult handlerResult) {
		if (this.resultHandlers != null) {
			for (HandlerResultHandler resultHandler : this.resultHandlers) {
				if (resultHandler.supports(handlerResult)) {
					return resultHandler;
				}
			}
		}
		throw new IllegalStateException("No HandlerResultHandler for " + handlerResult.getReturnValue());
	}

	@Override
	public Mono<Void> handlePreFlight(ServerWebExchange exchange) {
		return Flux.fromIterable(this.handlerMappings != null ? this.handlerMappings : Collections.emptyList())
				.concatMap(mapping -> mapping.getHandler(exchange))
				.switchIfEmpty(Mono.fromRunnable(() -> exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN)))
				.next()
				.then();
	}

}
