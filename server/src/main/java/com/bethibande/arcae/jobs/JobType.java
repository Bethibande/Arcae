package com.bethibande.arcae.jobs;

public enum JobType {

    DELETE_OLD_VERSIONS,
    DELETE_EXPIRED_SESSIONS,
    CLEAN_UP_ORPHANED_FILES,
    CLEAN_UP_EXPIRED_UPLOADS,

    // One-off jobs
    UPDATE_SEARCH_INDEX,
    RESET_PASSWORD

}
