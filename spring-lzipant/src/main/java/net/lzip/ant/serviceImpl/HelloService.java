package net.lzip.ant.serviceImpl;

import org.springframework.stereotype.Service;

@Service(value = "HelloService")
public class HelloService {

	public String sayHello(String name) {
		return "[spring-framework] hello : " + name;
	}
}
