/**
 * Copyright 2016 Symantec Corporation.
 * 
 * Licensed under the Apache License, Version 2.0 (the “License”); 
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.symcpe.wraith.rules.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import io.symcpe.wraith.actions.Action;
import io.symcpe.wraith.actions.alerts.AlertAction;
import io.symcpe.wraith.rules.Rule;

/**
 * {@link Validator} for {@link Action} associated with a {@link Rule}
 * 
 * @author ambud_sharma
 */
public class ActionValidator implements Validator<Action> {

	private List<Validator<Action>> conditionValidators = new ArrayList<>();
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$");

	@SuppressWarnings("unchecked")
	@Override
	public void configure(List<Validator<?>> validators) {
		for (Validator<?> validator : validators) {
			try {
				conditionValidators.add((Validator<Action>) validator);
			} catch (Exception e) {
			}
		}
	}

	@Override
	public void validate(Action action) throws ValidationException {
		if (action instanceof AlertAction) {
			AlertAction alertAction = (AlertAction) action;
			if (alertAction.getTarget() == null || alertAction.getTarget().trim().isEmpty()) {
				throw new ValidationException("Alert target can't be empty");
			}
			if (alertAction.getMedia() == null || alertAction.getMedia().trim().isEmpty()) {
				throw new ValidationException("Alert media can't be empty");
			}
			if (alertAction.getBody() == null || alertAction.getBody().trim().isEmpty()) {
				throw new ValidationException("Alert body can't be empty");
			}
			if (alertAction.getMedia().contains("mail")) {
				String[] emails = alertAction.getTarget().split("\\s{0,1},");
				for (String email : emails) {
					if (!EMAIL_PATTERN.matcher(email).matches()) {
						throw new ValidationException("Not a valid email address:"+email);
					}
				}
			}
		} else {
			// unsupported action
			throw new ValidationException("Unsupported action type");
		}
		for (Validator<Action> validator : conditionValidators) {
			validator.validate(action);
		}
	}

}
