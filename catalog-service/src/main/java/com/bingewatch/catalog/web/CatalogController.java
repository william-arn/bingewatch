package com.bingewatch.catalog.web;

import com.bingewatch.catalog.domain.Movie;
import com.bingewatch.catalog.service.CatalogService;
import com.bingewatch.catalog.web.dto.MovieRequest;
import com.bingewatch.catalog.web.dto.MovieResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/movies")
public class CatalogController {
    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<MovieResponse>> findAll(
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) Integer releaseYear) {
        List<MovieResponse> body = service.findAll(genre,releaseYear).stream()
                .map(MovieResponse::from)
                .toList();

        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MovieResponse> findById(@PathVariable Long id) {
        return service.findById(id)
                .map(MovieResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<MovieResponse> create(@RequestBody MovieRequest request) {
        Movie movie = service.create(request.toEntity());
        return ResponseEntity.status(HttpStatus.CREATED).body(MovieResponse.from(movie));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
