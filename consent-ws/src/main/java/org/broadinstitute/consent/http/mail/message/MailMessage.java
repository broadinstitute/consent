package org.broadinstitute.consent.http.mail.message;

import com.sendgrid.Content;
import com.sendgrid.Email;
import com.sendgrid.Mail;
import com.sendgrid.Personalization;

import javax.mail.MessagingException;
import java.io.Writer;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public abstract class MailMessage {

    protected Mail generateEmailMessage(String toAddress, String fromAddress, Writer template, String referenceId, String type) throws MessagingException {
        return generateEmailMessage(Collections.singletonList(toAddress), fromAddress, template, referenceId, type).get(0);
    }

    protected List<Mail> generateEmailMessage(List<String> toAddresses, String fromAddress, Writer template, String referenceId, String type) throws MessagingException {
        if (toAddresses == null || toAddresses.isEmpty()) {
            throw new MessagingException("List of to-addresses cannot be empty");
        }
        Content content = new Content("text/html", template.toString());
        String subject = assignSubject(referenceId, type);
        return toAddresses.stream().map(
            address -> {
                Mail mail = new Mail();
                mail.setFrom(new Email(fromAddress));
                mail.setSubject(subject);
                mail.addContent(content);
                Personalization personalization = new Personalization();
                personalization.addTo(new Email(address));
                mail.addPersonalization(personalization);
                return mail;
            }).collect(Collectors.toList());
    }

    abstract String assignSubject(String referenceId, String type);

}