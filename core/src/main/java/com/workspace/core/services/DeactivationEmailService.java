package com.workspace.core.services;

import com.workspace.core.models.DeactivationNotification;

import java.util.List;

public interface DeactivationEmailService {

    void send(
            DeactivationNotification notification,
            List<String> recipients);
}