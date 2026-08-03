package com.linkflow.user.infrastructure.adapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, Long> {
    List<RoleEntity> findByIdIn(Collection<Long> ids);
    List<RoleEntity> findByNameIn(Collection<String> names);
}
