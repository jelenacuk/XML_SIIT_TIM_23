package com.papershare.papershare.controller;

import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.exist.http.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.xml.sax.SAXException;
import org.xmldb.api.base.XMLDBException;

import com.papershare.papershare.DTO.AddReviewDTO;
import com.papershare.papershare.DTO.ReviewDTO;
import com.papershare.papershare.DTO.SendReviewDTO;
import com.papershare.papershare.service.ReviewService;

@RestController
@RequestMapping("/api/review")
@CrossOrigin
public class ReviewController {

	private ReviewService reviewService;

	public ReviewController(ReviewService reviewService) {
		this.reviewService = reviewService;
	}

	@PostMapping(value = "", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> addReview(@RequestBody AddReviewDTO dto)
			throws ClassNotFoundException, InstantiationException, IllegalAccessException, XMLDBException,
			NotFoundException, MailException, InterruptedException {
		reviewService.addReview(dto);
		return new ResponseEntity<String>(
				"Successfully assigned review for: " + dto.getPublicationName() + " to: " + dto.getUsername(),
				HttpStatus.OK);
	}

	@GetMapping(value = "/recommendReviewers/{title}")
	public ResponseEntity<List<String>> recommendReviewers(@PathVariable("title") String title)
			throws ClassNotFoundException, InstantiationException, IllegalAccessException, XMLDBException,
			ParserConfigurationException, SAXException, IOException {
		List<String> usernames = reviewService.recommendReviewers(title);

		return new ResponseEntity<>(usernames, HttpStatus.OK);
	}

	@GetMapping(value = "/userReviews")
	public ResponseEntity<List<ReviewDTO>> userReviews() throws XMLDBException {
		List<ReviewDTO> reviews = reviewService.findReviewsByUser();

		return new ResponseEntity<>(reviews, HttpStatus.OK);
	}

	@GetMapping(value = "/submittedReviews")
	public ResponseEntity<List<ReviewDTO>> submittedReviews() throws XMLDBException {
		List<ReviewDTO> reviews = reviewService.findSubmittedReviews();

		return new ResponseEntity<>(reviews, HttpStatus.OK);
	}

	@GetMapping(value = "/paperReviews/{publicationName}")
	public ResponseEntity<List<ReviewDTO>> paperReviews(@PathVariable("publicationName") String publicationName)
			throws XMLDBException {
		List<ReviewDTO> reviews = reviewService.findReviewsByPaper(publicationName);
		return new ResponseEntity<>(reviews, HttpStatus.OK);
	}

	@GetMapping(value = "/{name}")
	public ResponseEntity<String> getReviewHTML(@PathVariable("name") String name) {
		String result = reviewService.convertXMLtoHTML(name);
		return new ResponseEntity<String>(result, HttpStatus.OK);
	}

	@GetMapping(value = "/united/{name}")
	public ResponseEntity<String> getUnitedReviewHTML(@PathVariable("name") String name) {
		System.out.println("Rev Name: " + name);
		String result = reviewService.convertUnitedReviewToHTML(name);
		return new ResponseEntity<String>(result, HttpStatus.OK);
	}

	@GetMapping(value = "/asText/{name}", produces = MediaType.TEXT_XML_VALUE)
	public ResponseEntity<String> getReviewAsText(@PathVariable("name") String name) throws TransformerException {
		String result = reviewService.getReviewAsText(name);
		return new ResponseEntity<String>(result, HttpStatus.OK);
	}

	@GetMapping(value = "accept/{reviewId}")
	public ResponseEntity<String> accept_review(@PathVariable("reviewId") String reviewId)
			throws MailException, InterruptedException {
		reviewService.acceptReview(reviewId);
		return new ResponseEntity<String>("Review has been accepted successfully", HttpStatus.OK);
	}

	@GetMapping(value = "reject/{reviewId}")
	public ResponseEntity<String> reject_review(@PathVariable("reviewId") String reviewId)
			throws ClassNotFoundException, InstantiationException, IllegalAccessException, XMLDBException,
			MailException, InterruptedException {
		reviewService.rejectReview(reviewId);
		return new ResponseEntity<String>("Review has been rejected successfully", HttpStatus.OK);
	}

	@GetMapping(value = "publish/{reviewId}")
	public ResponseEntity<String> publish_review(@PathVariable("reviewId") String reviewId)
			throws MailException, InterruptedException {
		reviewService.publishReview(reviewId);
		return new ResponseEntity<String>("Review has been published successfully", HttpStatus.OK);
	}

	// Reviewer sending review
	@PostMapping(value = "/send", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> sendReview(@RequestBody SendReviewDTO dto)
			throws ClassNotFoundException, InstantiationException, IllegalAccessException, ParserConfigurationException,
			SAXException, IOException, TransformerException, XMLDBException {
		reviewService.sendReview(dto);
		return new ResponseEntity<String>("Review has been sent successfully", HttpStatus.OK);
	}

	@PostMapping(value = "/sendReviewsToAuthor/{paperName}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> sendReviewsToAuthor(@PathVariable("paperName") String paperName)
			throws MailException, InterruptedException {
		reviewService.sendReviewsToAuthor(paperName);
		return new ResponseEntity<>(HttpStatus.OK);
	}
}
