package nl.runnable.alfresco.webscripts.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface Authentication {
	/**
	 * Determines the {@link AuthenticationType}.
	 */
	AuthenticationType value() default AuthenticationType.USER;

	/**
	 * Determines the user the Web Script is run as.
	 * 
	 * @return
	 */
	String runAs() default "";

}
