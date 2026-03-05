package com.bethibande.repository.repository;

import com.bethibande.repository.jpa.files.FileUploadSession;

public interface HasUploadSessions {

    void abortUploadSession(final FileUploadSession session);

}
