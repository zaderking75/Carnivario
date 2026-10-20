package duocuc.cl.rodrigo.carniverocrud.messaging;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;

/** Lista explícita de fallos transitorios; los fallos desconocidos no se reintentan. */
final class NotificationRetryPolicy {
    private NotificationRetryPolicy() {}

    static boolean isRetryable(RuntimeException failure) {
        if (!(failure instanceof MailSendException)) return false;
        var pending = new ArrayDeque<Throwable>();
        var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        pending.add(failure);
        boolean transientFailure = false;
        while (!pending.isEmpty()) {
            Throwable cause = pending.removeFirst();
            if (!visited.add(cause)) continue;
            if (cause instanceof MailAuthenticationException || cause instanceof AuthenticationFailedException
                    || cause instanceof MailParseException || cause instanceof MailPreparationException
                    || cause instanceof AddressException) return false;
            int smtpCode = cause instanceof SMTPSendFailedException smtp ? smtp.getReturnCode()
                    : cause instanceof SMTPAddressFailedException address ? address.getReturnCode() : 0;
            if (smtpCode >= 500) return false;
            if (smtpCode >= 400 || cause instanceof ConnectException
                    || cause instanceof SocketTimeoutException || cause instanceof SocketException) {
                transientFailure = true;
            }
            if (cause.getCause() != null) pending.add(cause.getCause());
            if (cause instanceof MessagingException mail && mail.getNextException() != null) {
                pending.add(mail.getNextException());
            }
            if (cause instanceof MailSendException mail) {
                for (Exception nested : mail.getMessageExceptions()) pending.add(nested);
            }
        }
        return transientFailure;
    }
}
