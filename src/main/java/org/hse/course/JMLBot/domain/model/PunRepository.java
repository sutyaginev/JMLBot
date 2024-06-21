package org.hse.course.JMLBot.domain.model;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface PunRepository extends CrudRepository<Pun, Long> {

    // Запрос для поиска максимального id
    @Query("SELECT max(id) FROM puns_table")
    Long findMaxId();
}
