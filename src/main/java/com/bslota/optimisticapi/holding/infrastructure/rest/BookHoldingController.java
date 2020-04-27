package com.bslota.optimisticapi.holding.infrastructure.rest;

import com.bslota.optimisticapi.holding.aggregate.Version;
import com.bslota.optimisticapi.holding.application.BookConflictIdentified;
import com.bslota.optimisticapi.holding.application.BookNotFound;
import com.bslota.optimisticapi.holding.application.BookPlacedOnHold;
import com.bslota.optimisticapi.holding.application.PlaceOnHoldCommand;
import com.bslota.optimisticapi.holding.application.PlacingOnHold;
import com.bslota.optimisticapi.holding.application.Result;
import com.bslota.optimisticapi.holding.domain.BookId;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

import static com.bslota.optimisticapi.holding.domain.Status.PLACED_ON_HOLD;

@RestController
@RequestMapping("/books")
class BookHoldingController {

    private final PlacingOnHold placingOnHold;

    BookHoldingController(PlacingOnHold placingOnHold) {
        this.placingOnHold = placingOnHold;
    }

    @PatchMapping(path = "/{bookId}")
    ResponseEntity<?> updateBookStatus(@PathVariable("bookId") UUID bookId,
                                       @RequestBody UpdateBookStatus command,
                                       @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) ETag ifMatch) {
        if (PLACED_ON_HOLD.equals(command.getStatus())) {
            return Optional.ofNullable(ifMatch)
                    .map(eTag -> handle(bookId, command, eTag))
                    .orElse(preconditionFailed());
        } else {
            return ResponseEntity.ok().build(); //we do not care about it now
        }
    }

    private ResponseEntity<?> handle(UUID bookId, UpdateBookStatus command, ETag ifMatch) {
        Version version = Version.from(Long.parseLong(ifMatch.getTrimmedValue()));
        PlaceOnHoldCommand placeOnHoldCommand = PlaceOnHoldCommand.commandFor(BookId.of(bookId), command.patronId())
                .with(version);
        Result result = placingOnHold.handle(placeOnHoldCommand);
        return buildResponseFrom(result);
    }

    private ResponseEntity<?> buildResponseFrom(Result result) {
        if (result instanceof BookPlacedOnHold) {
            return ResponseEntity.noContent().build();
        } else if (result instanceof BookNotFound) {
            return ResponseEntity.notFound().build();
        } else if (result instanceof BookConflictIdentified) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).build();
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private ResponseEntity preconditionFailed() {
        return ResponseEntity
                .status(HttpStatus.PRECONDITION_REQUIRED)
                .body(ErrorMessage.from("If-Match header is required"));
    }

}
