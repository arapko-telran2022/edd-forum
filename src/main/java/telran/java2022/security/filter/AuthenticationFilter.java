package telran.java2022.security.filter;

import java.io.IOException;
import java.security.Principal;
import java.util.Base64;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import telran.java2022.accounting.dao.UserAccountRepository;
import telran.java2022.accounting.model.UserAccount;
import telran.java2022.security.context.SecurityContext;
import telran.java2022.security.context.User;
import telran.java2022.security.service.SessionService;

@Component
@RequiredArgsConstructor
@Order(10)
public class AuthenticationFilter implements Filter {

	final UserAccountRepository userAccountRepository;
	final SecurityContext context;
	final SessionService sessionService;

	@Override
	public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) req;
		HttpServletResponse response = (HttpServletResponse) resp;

		if (checkEndPoint(request.getMethod(), request.getServletPath())) {
			//Get initial values
			String token = request.getHeader("Authorization");
			String sessionId = request.getSession().getId();
			UserAccount userAccount = sessionService.getUser(sessionId);
			
			//First access without token
			if(token == null && userAccount == null) {
				response.sendError(401, "Invalid token and session ID");
				return;
			}
			
			//Every time when we have token delete old session user and create new   
			if (token != null) {
				//DELETE old session user if exist 
				sessionService.removeUser(sessionId);
				
				//Check credentials
				String[] credentials;
				try {
					credentials = getCredentialsFromToken(token);
				} catch (Exception e) {
					response.sendError(401, "Invalid token");
					return;
				}
				
				//Check user in DB
				userAccount = userAccountRepository.findById(credentials[0]).orElse(null);
				if (userAccount == null || !BCrypt.checkpw(credentials[1], userAccount.getPassword())) {
					response.sendError(401, "login or password is invalid");
					return;
				}
				
				//Create new session user
				String newSessionId = request.changeSessionId();
				sessionService.addUser(newSessionId, userAccount);
				
			}	
			
			// We here because  token != null OR userAccount != null
			request = new WrappedRequest(request, userAccount.getLogin());
			User user = User.builder().userName(userAccount.getLogin()).password(userAccount.getPassword())
					.roles(userAccount.getRoles()).build();
			context.addUser(user);
		}
		chain.doFilter(request, response);
	}

	private String[] getCredentialsFromToken(String token) {
		String[] basicAuth = token.split(" ");
		String decode = new String(Base64.getDecoder().decode(basicAuth[1]));
		String[] credentials = decode.split(":");
		return credentials;
	}

	private boolean checkEndPoint(String method, String servletPath) {
		return !("POST".equalsIgnoreCase(method) && servletPath.matches("/account/register/?")
				|| (servletPath.matches("/forum/posts(/\\w+)+/?")));
	}

	private class WrappedRequest extends HttpServletRequestWrapper {
		String login;

		public WrappedRequest(HttpServletRequest request, String login) {
			super(request);
			this.login = login;
		}

		@Override
		public Principal getUserPrincipal() {
			return () -> login;
		}

	}

}
