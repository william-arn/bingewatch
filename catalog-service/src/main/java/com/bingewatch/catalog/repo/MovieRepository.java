package com.bingewatch.catalog.repo;

import com.bingewatch.catalog.domain.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MovieRepository extends JpaRepository<Movie,Long> {
    @Query("""
        select m from Movie m
        where (:genre is null or m.genre = :genre)
          and (:releaseYear is null or m.releaseYear = :releaseYear)
        """)
    List<Movie> findAll(@Param("genre") String genre,
                       @Param("releaseYear") Integer releaseYear);
}
