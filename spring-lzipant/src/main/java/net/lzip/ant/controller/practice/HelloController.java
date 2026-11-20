package net.lzip.ant.controller.practice;

import net.lzip.ant.model.SimStatus;
import net.lzip.ant.serviceImpl.HelloService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartFile;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * @author Harrison Lee
 * @description TODO
 * @date 2022/6/25 19:44
 */
@RestController
@RequestMapping("/hello")
public class HelloController {

	@Autowired
	private HelloService helloService;

	@GetMapping("/test")
	public SimStatus test() {
		/*Sim sim = new Sim();
		SimStatus ss = new SimStatus();
		ss.setSim(sim);
		BeanWrapper ssWrapper = new BeanWrapperImpl(ss);
		ssWrapper.setPropertyValue("lifecycle", 6);
		ssWrapper.setPropertyValue("sim.msisdn", "1234567890");
		System.out.println(ssWrapper.getPropertyValue("sim.iccid"));

		return ss;*/
		return null;
	}

	@GetMapping("/greet/{name}")
	public String hello(@PathVariable String name) {
		return helloService.sayHello(name);
	}

	@Async
	@GetMapping("/async_greet/{name}")
	public Future<String> asyncGreet(@PathVariable String name) {
		return CompletableFuture.supplyAsync(() -> helloService.sayHello(name));
	}

	@PostMapping("/uploadFile")
	public String uploadFile(@RequestParam MultipartFile file, String name, @RequestParam(defaultValue = "24") int age) {
		System.out.println("name : " + name);
		System.out.println("age: " + age);
		return file.getOriginalFilename();
	}

}
