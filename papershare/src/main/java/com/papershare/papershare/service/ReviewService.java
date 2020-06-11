package com.papershare.papershare.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import org.exist.http.NotFoundException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.papershare.papershare.DTO.AddReviewDTO;
import com.papershare.papershare.DTO.ReviewDTO;
import com.papershare.papershare.database.ExistManager;
import com.papershare.papershare.dom.DOMParser;
import com.papershare.papershare.dom.XSLTransformer;
import com.papershare.papershare.exception.UserAlreadyAssignedException;
import com.papershare.papershare.model.TUser;
import com.papershare.papershare.model.review.Review;
import com.papershare.papershare.repository.PaperRepository;
import com.papershare.papershare.repository.ReviewRepository;
import com.papershare.papershare.repository.UserRepository;

@Service
public class ReviewService {
	private final String reviewXSL = "src/main/resources/data/xsl/review.xsl";

	private ReviewRepository reviewRepository;
	private XSLTransformer xslTransformer;
	private UserRepository userRepository;
	private PaperRepository paperRepository;
	private ExistManager existManager;
	private DOMParser domParser;

	public ReviewService(ReviewRepository repository, XSLTransformer xslTransformer, UserRepository userRepository,
			PaperRepository paperRepository, ExistManager existManager, DOMParser domParser) {
		this.reviewRepository = repository;
		this.xslTransformer = xslTransformer;
		this.userRepository = userRepository;
		this.paperRepository = paperRepository;
		this.existManager = existManager;
		this.domParser = domParser;
	}

	public Review findById(String id) {
		return reviewRepository.findById(id);
	}

	public void addReview(AddReviewDTO dto) throws ClassNotFoundException, InstantiationException,
			IllegalAccessException, XMLDBException, NotFoundException {

		if (!dto.getPublicationName().endsWith(".xml")) {
			dto.setPublicationName(dto.getPublicationName() + ".xml");
		}

		TUser user = userRepository.findOneByUsername(dto.getUsername());
		if (user == null) {
			throw new UsernameNotFoundException(String.format("No user found with username '%s'.", dto.getUsername()));
		}

		Document xml = paperRepository.findScientificPaper(dto.getPublicationName());
		if (xml == null) {
			throw new NotFoundException("Scientific paper with name: " + dto.getPublicationName() + " not found.");
		}

		// Checks if given user is already assigned for the same scientific paper.
		String xPathExpression = String.format("/review[metadata/publicationName='%s' and metadata/reviewer='%s']",
				dto.getPublicationName(), dto.getUsername());
		ResourceSet alreadyAssigned = findReviews(xPathExpression);
		if (alreadyAssigned.getSize() > 0) {
			throw new UserAlreadyAssignedException("User with username: " + dto.getUsername()
					+ " is already assigned for review on paper: " + dto.getPublicationName());
		}

		long submissionDate = System.currentTimeMillis();
		String id = "rev" + submissionDate + ".xml";

		String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

		String review = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
				+ "<review xmlns=\"https://github.com/MilePrastalo/XML_SIIT_TIM_23\" xmlns:rv=\"https://github.com/MilePrastalo/XML_SIIT_TIM_23\">\r\n"
				+ "    <metadata>\r\n" + "     	<reviewer>" + dto.getUsername() + "</reviewer>\r\n"
				+ "        <publicationName>" + dto.getPublicationName() + "</publicationName>\r\n"
				+ "        <submissionDate>" + today + "</submissionDate>\r\n" + "    </metadata>\r\n"
				+ "    <body>\r\n" + "        <criteriaEvaluation>\r\n"
				+ "            <relevanceOfResearchProblem></relevanceOfResearchProblem>\r\n"
				+ "            <introduction></introduction>\r\n"
				+ "            <conceptualQuality></conceptualQuality>\r\n"
				+ "            <methodologicalQuality></methodologicalQuality>\r\n"
				+ "            <results></results>\r\n" + "            <discussion></discussion>\r\n"
				+ "            <readability></readability>\r\n" + "        </criteriaEvaluation>\r\n"
				+ "        <overallEvaluation></overallEvaluation>\r\n" + "        <commentsToAuthor>\r\n"
				+ "        </commentsToAuthor>\r\n" + "        <commentsToEditor></commentsToEditor>\r\n"
				+ "    </body>\r\n" + "</review>";
		reviewRepository.save(review, id);

		String xmlPatch = "reviewing";

		existManager.update(0, paperRepository.getCollectionId(), dto.getPublicationName() + ".xml",
				"/ScientificPaper/status", xmlPatch);
	}

	public ArrayList<ReviewDTO> findReviewsByUser() throws XMLDBException {
		String username = getLoggedUser();
		String xPathExpression = String.format("/review[metadata/reviewer='%s']", username);
		ResourceSet result = reviewRepository.findReviews(xPathExpression);
		System.out.println(result.getSize());
		ArrayList<ReviewDTO> reviews = extractDataFromReviews(result);

		return reviews;
	}
	
	public ArrayList<ReviewDTO> findReviewsByPaper(String publicationName) throws XMLDBException {
		String xPathExpression = String.format("/review[metadata/publicationName='%s']", publicationName);
		ResourceSet result = reviewRepository.findReviews(xPathExpression);
		ArrayList<ReviewDTO> reviews = extractDataFromReviews(result);
		return reviews;
	}

	private ArrayList<ReviewDTO> extractDataFromReviews(ResourceSet resourceSet) {
		ArrayList<ReviewDTO> reviews = new ArrayList<ReviewDTO>();
		ResourceIterator i;
		try {
			i = resourceSet.getIterator();
			while (i.hasMoreResources()) {
				XMLResource resource = (XMLResource) i.nextResource();
				Document document = domParser.buildDocumentFromText(resource.getContent().toString());
				NodeList publicationName = document.getElementsByTagName("publicationName");
				NodeList reviewer = document.getElementsByTagName("reviewer");
				NodeList submissionDate = document.getElementsByTagName("submissionDate");
				reviews.add(new ReviewDTO(resource.getDocumentId(), publicationName.item(0).getTextContent(),
						reviewer.item(0).getTextContent(), submissionDate.item(0).getTextContent()));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return reviews;
	}

	public ResourceSet findReviews(String xPathExpression) {
		ResourceSet result = null;
		try {
			result = existManager.retrieve(reviewRepository.getCollectionId(), xPathExpression);
			return result;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	public String convertXMLtoHTML(String id) {
		Document xml = reviewRepository.findByName(id);
		return xslTransformer.convertXMLtoHTML(reviewXSL, xml);
	}

	private String getLoggedUser() {
		String username = null;
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (!(authentication instanceof AnonymousAuthenticationToken)) {
			username = authentication.getName();
		}
		return username;
	}

}
