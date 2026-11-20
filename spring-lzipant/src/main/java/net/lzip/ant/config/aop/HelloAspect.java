package net.lzip.ant.config.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@Aspect
public class HelloAspect {
	private final Logger logger = LoggerFactory.getLogger(HelloAspect.class);

	final String pointcut = "execution(* net.lzip.ant.serviceImpl.HelloService.sayHello(..))";

	@Pointcut(pointcut)
	public void pointcut() {}

	@Before("pointcut()")
	public void before(JoinPoint joinPoint) {
		logger.info("BEFORE: ");
		String name = getName(joinPoint);
		if (name == null || name.length() < 5) {
			logger.warn("BEFORE: the length is name is too short.");
		}
	}

	@After("pointcut()")
	public void after(JoinPoint joinPoint) {
		logger.info("AFTER: ");
	}

	@Around("pointcut()")
	public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
		logger.info("Around: ");
		String name = getName(joinPoint);
		name = name.toUpperCase();
		return (String) joinPoint.proceed(new String[]{name});
	}

	@AfterReturning("pointcut()")
	public void afterReturning(JoinPoint joinPoint) {
		logger.info("AfterReturning: ");
	}

	@AfterThrowing(value = "pointcut()", throwing = "throwable")
	public void afterThrowing(JoinPoint joinPoint, Throwable throwable) {
		logger.info("AfterThrowing: ");
		if (throwable != null) {
			logger.error(throwable.getMessage());
		}
	}

	private String getName(JoinPoint joinPoint) {
		Object[] args = joinPoint.getArgs();
		return (String) args[0];
	}
}
