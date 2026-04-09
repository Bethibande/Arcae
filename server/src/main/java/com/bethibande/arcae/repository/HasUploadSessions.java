package com.bethibande.arcae.repository;

import com.bethibande.arcae.jpa.files.FileUploadSession;

public interface HasUploadSessions {

    void abortUploadSession(final FileUploadSession session);

}
