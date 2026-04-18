package com.bethibande.arcae.jobs;

public enum JobType {

    DELETE_OLD_VERSIONS,
    DELETE_EXPIRED_SESSIONS,
    // Yes, this should probably be CLEANUP_ORPHANED_FILES instead, but I cannot be bothered to write a DB migration for this typo, so let's ust stick with it...
    CLEAN_UP_ORPHANED_FILES,
    CLEAN_UP_EXPIRED_UPLOADS,
    CLEAN_UP_DATABASE,

    // One-off jobs
    UPDATE_SEARCH_INDEX,
    RESET_PASSWORD

}
