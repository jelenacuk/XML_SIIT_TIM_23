package com.papershare.papershare.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.papershare.papershare.DTO.PaperUploadDTO;
import com.papershare.papershare.DTO.PaperViewDTO;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import com.papershare.papershare.dom.DOMParser;
import com.papershare.papershare.dom.XSLTransformer;
import com.papershare.papershare.exception.NotAnAuthorException;
import com.papershare.papershare.exception.PaperAlreadyExistException;
import com.papershare.papershare.repository.PaperRepository;
import com.papershare.papershare.repository.ReviewRepository;

@Service
public class PaperService {

	private final String scientificPublicatonXSL = "src/main/resources/data/xsl/scientificPaper.xsl";
	private final String paperSchema = "src/main/resources/data/scientificPaper.xsd";
	private static String xslFOPath = "src/main/resources/data/xsl/paperToPDF.xsl";
	private DOMParser domParser;

	private PaperRepository paperRepository;
	private ReviewRepository reviewRepository;
	private XSLTransformer xslTransformer;

	public PaperService(XSLTransformer xslTransformer, PaperRepository sciPaperRepository, DOMParser domParser,
			ReviewRepository reviewRepository) {
		this.paperRepository = sciPaperRepository;
		this.xslTransformer = xslTransformer;
		this.domParser = domParser;
		this.reviewRepository = reviewRepository;
	}

	public String convertXMLtoHTML(String name) {
		Document xml = paperRepository.findScientificPaper(name);
		return xslTransformer.convertXMLtoHTML(scientificPublicatonXSL, xml);
	}

	public void savePaper(PaperUploadDTO dto)
			throws ParserConfigurationException, SAXException, IOException, TransformerException,
			ClassNotFoundException, InstantiationException, IllegalAccessException, XMLDBException, PaperAlreadyExistException {
		Document document = domParser.buildDocumentFromText(dto.getText());
		NodeList nodeList = document.getElementsByTagName("ScientificPaper");
		Element sp = (Element) nodeList.item(0);
		NodeList ndTitle = document.getElementsByTagName("sci:title");
		String title = ndTitle.item(0).getTextContent();
		Document prev = null;
		try {
			prev = paperRepository.findScientificPaper(title);
		}
		catch (Exception e) {
			System.out.println("caught");
		}
		if(prev != null) {
			throw new PaperAlreadyExistException("Paper already exist");
		}
		Element status = document.createElement("sci:status");
		status.appendChild(document.createTextNode("not completed"));
		sp.appendChild(status);
		
		System.out.println(dto.getText());
		Element chaptersMain = (Element) (document.getElementsByTagName("sci:Chapters")).item(0);
		NodeList chapters = chaptersMain.getElementsByTagName("sci:Chapter");
		Long currentMilli = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		for (int i = 0; i < chapters.getLength(); i++) {
			Element chapter = (Element) chapters.item(i);
			chapter.setAttribute("id", currentMilli + "" + i);
		}
		SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
		String recievedDate = sdf.format(new Date());
		sp.setAttribute("id", currentMilli.toString());

		StringWriter sw = new StringWriter();
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
		transformer.setOutputProperty(OutputKeys.METHOD, "xml");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

		transformer.transform(new DOMSource(document), new StreamResult(sw));

		paperRepository.save(sw.toString(), title + ".xml");

		String coverLetter = "<coverLetter><authorUsername></authorUsername><Content>" + dto.getCoverLetter()
				+ "</Content><title>" + title + "</title></coverLetter>";
		paperRepository.saveCoverLetter(coverLetter);
	}
	
	public void updatePaper(PaperUploadDTO dto) throws ParserConfigurationException, SAXException, IOException, TransformerException, ClassNotFoundException, InstantiationException, IllegalAccessException, XMLDBException {
		System.out.println(dto.getText());
		Document document = domParser.buildDocumentFromText(dto.getText());
		NodeList nodeList = document.getElementsByTagName("ScientificPaper");
		Element sp = (Element) nodeList.item(0);
		NodeList ndTitle = document.getElementsByTagName("sci:title");
		String title = ndTitle.item(0).getTextContent();
		
		System.out.println(dto.getText());
		Element chaptersMain = (Element) (document.getElementsByTagName("sci:Chapters")).item(0);
		NodeList chapters = chaptersMain.getElementsByTagName("sci:Chapter");
		Long currentMilli = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		for (int i = 0; i < chapters.getLength(); i++) {
			Element chapter = (Element) chapters.item(i);
			if(chapter.getAttribute("id").equals("")) {
				chapter.setAttribute("id", currentMilli + "" + i);
			}
		}
		StringWriter sw = new StringWriter();
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		transformer.setOutputProperty(OutputKeys.METHOD, "xml");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		transformer.transform(new DOMSource(document), new StreamResult(sw));

		
		System.out.println(sw.toString());
		String xmlFragmet = sw.toString();
		if(!title.endsWith(".xml")) {
			title += ".xml";
		}
		paperRepository.removePaper(title);
		paperRepository.save(xmlFragmet, title);
	}

	public Resource getPdf(String name) throws Exception {
		Document document = paperRepository.findScientificPaper(name);
		StringWriter sw = new StringWriter();
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
		transformer.setOutputProperty(OutputKeys.METHOD, "xml");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

		transformer.transform(new DOMSource(document), new StreamResult(sw));
		ByteArrayOutputStream outputStream = xslTransformer.generatePDf(sw.toString(), xslFOPath);

		Path file = Paths.get(name + ".pdf");
		Files.write(file, outputStream.toByteArray());

		return new UrlResource(file.toUri());
	}

	public void changePaperStatus(String paperName, String status) {
		String documentId;
		if (!paperName.endsWith(".xml")) {
			documentId = paperName + ".xml";
		} else {
			documentId = paperName;
		}
		String targetElement = "/ScientificPaper/status";
		String xmlFragmet = status;
		paperRepository.modifyPaper(documentId, targetElement, xmlFragmet);
	}

	public ArrayList<PaperViewDTO> getAllPapers() {
		String xPathExpression = "/ScientificPaper";
		ResourceSet result = paperRepository.findPapers(xPathExpression);
		ArrayList<PaperViewDTO> paperList = extractDataFromPapers(result);
		return paperList;
	}

	public ArrayList<PaperViewDTO> findCompletedPapers() {
		String xPathExpression = "/ScientificPaper[status = 'completed']";
		ResourceSet result = paperRepository.findPapers(xPathExpression);
		ArrayList<PaperViewDTO> paperList = extractDataFromPapers(result);
		return paperList;
	}

	public ArrayList<PaperViewDTO> findPapersByUser() {
		String username = getLoggedUser();
		String xPathExpression = String.format("/ScientificPaper[Authors/Author/authorUsername='%s' and status!='deleted']", username);
		ResourceSet result = paperRepository.findPapers(xPathExpression);
		ArrayList<PaperViewDTO> paperList = extractDataFromPapers(result);
		return paperList;
	}

	private ArrayList<PaperViewDTO> extractDataFromPapers(ResourceSet resourceSet) {
		ArrayList<PaperViewDTO> paperList = new ArrayList<PaperViewDTO>();
		ResourceIterator i;
		try {
			i = resourceSet.getIterator();
			while (i.hasMoreResources()) {
				XMLResource resource = (XMLResource) i.nextResource();
				Document document = domParser.buildDocumentFromText(resource.getContent().toString());
				String id = document.getElementsByTagName("ScientificPaper").item(0).getAttributes().getNamedItem("id")
						.getTextContent();
				NodeList authors = document.getElementsByTagName("sci:authorName");
				NodeList title = document.getElementsByTagName("sci:title");
				NodeList status = document.getElementsByTagName("sci:status");
				paperList.add(new PaperViewDTO(authors, title, status, id));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return paperList;
	}

	public void deletePaper(String publicationName) {

		if (!publicationName.endsWith(".xml")) {
			publicationName = publicationName + ".xml";
		}

		String user = getLoggedUser();
		Document document = paperRepository.findScientificPaper(publicationName);
		NodeList authors = document.getElementsByTagName("sci:authorUsername");
		List<String> listOfAuthors = new ArrayList<String>();
		for (int idx = 0, len = authors.getLength(); idx < len; idx++) {
			listOfAuthors.add(authors.item(idx).getTextContent());
		}
		if (!listOfAuthors.contains(user)) {
			throw new NotAnAuthorException("You must be one of the authors of this scientific paper: " + publicationName
					+ " , to be able to delete it");
		}

		changePaperStatus(publicationName, "deleted");

		String xPathExpression = String.format("/review[metadata/paperName='%s']",
				publicationName.substring(0, publicationName.length() - 4));
		ResourceSet result = reviewRepository.findReviews(xPathExpression);
		try {
			System.out.println(result.getSize());
		} catch (XMLDBException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		ResourceIterator i;
		try {
			i = result.getIterator();
			while (i.hasMoreResources()) {
				XMLResource resource = (XMLResource) i.nextResource();
				System.out.println(resource.getDocumentId());
				reviewRepository.removeReview(resource.getDocumentId());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private String getLoggedUser() {
		String username = null;
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (!(authentication instanceof AnonymousAuthenticationToken)) {
			username = authentication.getName();
		}
		return username;
	}
	public String getPaperAsText(String name) throws TransformerException {
		Document xml = paperRepository.findScientificPaper(name);
		StringWriter sw = new StringWriter();
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		transformer.setOutputProperty(OutputKeys.METHOD, "xml");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

		transformer.transform(new DOMSource(xml), new StreamResult(sw));
		System.out.println(sw.toString());
		return sw.toString();
	}
}
