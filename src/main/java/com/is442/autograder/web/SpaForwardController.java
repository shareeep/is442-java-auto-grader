package com.is442.autograder.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Forwards all non-API, non-static routes to index.html so React Router can
 * handle client-side routing.
 */
@Controller
public class SpaForwardController {

	@RequestMapping(value = {"/", "/{path:^(?!api|static|assets).*$}/**"})
	public String forward() {
		return "forward:/index.html";
	}
}
