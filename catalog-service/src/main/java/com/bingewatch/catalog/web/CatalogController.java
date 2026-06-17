package com.bingewatch.catalog.web;

import com.bingewatch.catalog.domain.Movie;
import com.bingewatch.catalog.repo.MovieRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/movies")
public class CatalogController {
    private final MovieRepository repo;

    public CatalogController(MovieRepository repo){this.repo = repo;}

    @GetMapping
    public List<Movie> findAll(@RequestParam(required = false) String genre){
        return (genre == null) ? repo.findAll() : repo.findByGenre(genre);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Movie> findById(@PathVariable Long id) {
        return repo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Movie create(@RequestBody Movie movie){
        return repo.save(movie);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id){
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

}
