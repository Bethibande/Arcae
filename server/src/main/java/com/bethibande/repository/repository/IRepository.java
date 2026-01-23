package com.bethibande.repository.repository;

import com.bethibande.repository.jpa.repository.Repository;
import com.bethibande.repository.repository.backend.IRepositoryBackend;

public interface IRepository {

    Repository getRepositoryInfo();

    IRepositoryBackend getBackend();

}
