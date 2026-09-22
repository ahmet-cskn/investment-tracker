package com.investmenttracker.investmentservice.error;

import com.investmenttracker.investmentservice.investment.InvestmentNotFoundException;
import com.investmenttracker.investmentservice.investment.UnknownInvestmentNameException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates exceptions into RFC 9457 problem detail responses. Standard Spring MVC exceptions
 * (malformed JSON, bad path variable types, unsupported methods, ...) are handled by the base class.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(InvestmentNotFoundException.class)
	public ProblemDetail handleNotFound(InvestmentNotFoundException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setTitle("Investment not found");
		return problem;
	}

	@ExceptionHandler(UnknownInvestmentNameException.class)
	public ProblemDetail handleUnknownName(UnknownInvestmentNameException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
		problem.setTitle("Invalid investment name");
		problem.setProperty("errors", List.of(new FieldValidationError("name", ex.getMessage())));
		return problem;
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"An unexpected error occurred");
		problem.setTitle("Internal server error");
		return problem;
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldValidationError> errors = ex.getBindingResult()
				.getFieldErrors()
				.stream()
				.map(error -> new FieldValidationError(error.getField(), error.getDefaultMessage()))
				.toList();
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
		problem.setTitle("Validation failed");
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	public record FieldValidationError(String field, String message) {

	}

}
