package io.symcpe.hendrix.api;

import javax.validation.ConstraintViolationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

	@Override
	public Response toResponse(final ConstraintViolationException exception) {
		return Response
				// Define your own status.
				.status(400)
				// Put an instance of Viewable in the response so that
				.entity(exception.getMessage()).build();
	}
}
