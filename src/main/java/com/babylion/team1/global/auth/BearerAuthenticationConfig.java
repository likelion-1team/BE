package com.babylion.team1.global.auth;

import com.babylion.team1.domain.user.repository.UserRepository;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class BearerAuthenticationConfig implements WebMvcConfigurer, HandlerMethodArgumentResolver {

	private static final Pattern BEARER = Pattern.compile("Bearer +([A-Za-z0-9._~-]+)", Pattern.CASE_INSENSITIVE);

	private final JwtTokens tokens;
	private final UserRepository users;

	public BearerAuthenticationConfig(@Lazy JwtTokens tokens, UserRepository users) {
		this.tokens = tokens;
		this.users = users;
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(this);
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(AuthenticatedUserId.class) && parameter.getParameterType() == Long.class;
	}

	@Override
	public Long resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
			NativeWebRequest request, WebDataBinderFactory binderFactory) {
		String[] headers = request.getHeaderValues(HttpHeaders.AUTHORIZATION);
		if (headers == null || headers.length != 1) {
			throw AuthException.unauthorized();
		}
		var matcher = BEARER.matcher(headers[0]);
		if (!matcher.matches()) {
			throw AuthException.unauthorized();
		}
		long userId = tokens.readAccessUserId(matcher.group(1));
		if (!users.existsById(userId)) {
			throw AuthException.unauthorized();
		}
		return userId;
	}
}
