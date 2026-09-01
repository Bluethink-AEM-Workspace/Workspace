package com.workspace.core.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileWriter;


import org.apache.commons.mail.Email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.day.cq.mailer.MessageGateway;
import com.day.cq.mailer.MessageGatewayService;


import io.wcm.testing.mock.aem.junit5.AemContextExtension;

@ExtendWith(AemContextExtension.class)
class AssetExpiryNotificationServiceTest {



@Mock
private MessageGatewayService messageGatewayService;

@Mock
private MessageGateway<Email> messageGateway;

@Mock
private AssetExpiryNotificationService.Config config;

private AssetExpiryNotificationService service;

private File reportFile;

private String[] recipients;

@BeforeEach
void setUp() throws Exception {

    MockitoAnnotations.openMocks(this);

    service = new AssetExpiryNotificationService();

    /*
     * Inject MessageGatewayService into the service.
     */
    java.lang.reflect.Field field =
            AssetExpiryNotificationService.class
                    .getDeclaredField("messageGatewayService");

    field.setAccessible(true);
    field.set(service, messageGatewayService);

    /*
     * Configure service.
     */
    when(config.environment())
            .thenReturn("author");

    when(config.authorUrl())
            .thenReturn("http://localhost:4502");

    when(config.subjectTemplate())
            .thenReturn("DAM Asset Expiry Report");

    service.activate(config);

    /*
     * Create a temporary CSV report file.
     */
    reportFile =
            File.createTempFile(
                    "workspace-asset-expiry-report",
                    ".csv");

    try (FileWriter writer =
                 new FileWriter(reportFile)) {

        writer.write(
                "Asset Name,Asset Path,Expiration Date,Author Link\n");

        writer.write(
                "product.pdf," +
                "/content/dam/workspace/product.pdf," +
                "2026-09-03," +
                "http://localhost:4502/content/dam/workspace/product.pdf\n");
    }

    reportFile.deleteOnExit();

    recipients =
            new String[] {
                "admin1@workspace.com",
                "admin2@workspace.com"
            };
}

@Test
void shouldSendExpiryReportSuccessfully()
        throws Exception {

    when(messageGatewayService.getGateway(Email.class))
            .thenReturn(messageGateway);

    boolean result =
            service.sendExpiryReport(
                    reportFile,
                    recipients,
                    1);

    assertTrue(result);

    verify(messageGatewayService)
            .getGateway(Email.class);

    verify(messageGateway)
            .send(any(Email.class));
}

@Test
void shouldReturnFalseWhenReportFileIsNull()
        throws Exception {

    boolean result =
            service.sendExpiryReport(
                    null,
                    recipients,
                    1);

    assertFalse(result);

    verify(
            messageGatewayService,
            never())
            .getGateway(any());

    verify(
            messageGateway,
            never())
            .send(any(Email.class));
}

@Test
void shouldReturnFalseWhenReportFileDoesNotExist()
        throws Exception {

    File missingFile =
            new File(
                    "target/non-existing-expiry-report.csv");

    boolean result =
            service.sendExpiryReport(
                    missingFile,
                    recipients,
                    1);

    assertFalse(result);

    verify(
            messageGatewayService,
            never())
            .getGateway(any());

    verify(
            messageGateway,
            never())
            .send(any(Email.class));
}

@Test
void shouldReturnFalseWhenRecipientsAreNull()
        throws Exception {

    boolean result =
            service.sendExpiryReport(
                    reportFile,
                    null,
                    1);

    assertFalse(result);

    verify(
            messageGatewayService,
            never())
            .getGateway(any());

    verify(
            messageGateway,
            never())
            .send(any(Email.class));
}

@Test
void shouldReturnFalseWhenRecipientsAreEmpty()
        throws Exception {

    boolean result =
            service.sendExpiryReport(
                    reportFile,
                    new String[0],
                    1);

    assertFalse(result);

    verify(
            messageGatewayService,
            never())
            .getGateway(any());

    verify(
            messageGateway,
            never())
            .send(any(Email.class));
}

@Test
void shouldReturnFalseWhenRecipientsContainOnlyBlankValues()
        throws Exception {

    String[] invalidRecipients =
            new String[] {
                null,
                "",
                "   ",
                "\t"
            };

    boolean result =
            service.sendExpiryReport(
                    reportFile,
                    invalidRecipients,
                    1);

    assertFalse(result);

    verify(
            messageGatewayService,
            never())
            .getGateway(any());

    verify(
            messageGateway,
            never())
            .send(any(Email.class));
}

@Test
void shouldReturnFalseWhenMessageGatewayIsUnavailable()
        throws Exception {

    when(messageGatewayService.getGateway(Email.class))
            .thenReturn(null);

    boolean result =
            service.sendExpiryReport(
                    reportFile,
                    recipients,
                    1);

    assertFalse(result);

    verify(messageGatewayService)
            .getGateway(Email.class);

    verify(
            messageGateway,
            never())
            .send(any(Email.class));
}

@Test
void shouldReturnFalseWhenSendingEmailFails()
        throws Exception {

    when(messageGatewayService.getGateway(Email.class))
            .thenReturn(messageGateway);

    org.mockito.Mockito.doThrow(
            new RuntimeException(
                    "SMTP connection failed"))
            .when(messageGateway)
            .send(any(Email.class));

    boolean result =
            service.sendExpiryReport(
                    reportFile,
                    recipients,
                    1);

    assertFalse(result);

    verify(messageGatewayService)
            .getGateway(Email.class);

    verify(messageGateway)
            .send(any(Email.class));
}

@Test
void shouldSendReportWhenAssetCountIsZero()
        throws Exception {

    when(messageGatewayService.getGateway(Email.class))
            .thenReturn(messageGateway);

    boolean result =
            service.sendExpiryReport(
                    reportFile,
                    recipients,
                    0);

    assertTrue(result);

    verify(messageGatewayService)
            .getGateway(Email.class);

    verify(messageGateway)
            .send(any(Email.class));
}

@Test
void shouldIgnoreNullAndBlankRecipientsAndSendToValidRecipients()
        throws Exception {

    when(messageGatewayService.getGateway(Email.class))
            .thenReturn(messageGateway);

    String[] mixedRecipients =
            new String[] {
                null,
                "",
                "   ",
                "admin1@workspace.com",
                "  admin2@workspace.com  "
            };

    boolean result =
            service.sendExpiryReport(
                    reportFile,
                    mixedRecipients,
                    1);

    assertTrue(result);

    verify(messageGatewayService)
            .getGateway(Email.class);

    verify(messageGateway)
            .send(any(Email.class));
}


}
