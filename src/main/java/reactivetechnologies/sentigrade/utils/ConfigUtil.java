package reactivetechnologies.sentigrade.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

import moa.classifiers.functions.SPegasos;
import moa.classifiers.trees.HoeffdingTree;
import weka.classifiers.bayes.NaiveBayesMultinomialUpdateable;
import weka.classifiers.bayes.NaiveBayesUpdateable;
import weka.classifiers.lazy.IBk;
import weka.classifiers.lazy.KStar;
import weka.classifiers.lazy.LWL;
import weka.core.Instances;

public class ConfigUtil {

	private static com.thoughtworks.xstream.XStream XSTREAM;
	
	private static void initXStream() {
		XSTREAM = new com.thoughtworks.xstream.XStream();
		XSTREAM.alias(Instances.class.getName(), Instances.class);
		XSTREAM.alias(NaiveBayesUpdateable.class.getName(), NaiveBayesUpdateable.class);
		XSTREAM.alias(NaiveBayesMultinomialUpdateable.class.getName(), NaiveBayesMultinomialUpdateable.class);
		XSTREAM.alias(HoeffdingTree.class.getName(), HoeffdingTree.class);
		XSTREAM.alias(SPegasos.class.getName(), SPegasos.class);
		XSTREAM.alias(IBk.class.getName(), IBk.class);
		XSTREAM.alias(KStar.class.getName(), KStar.class);
		XSTREAM.alias(LWL.class.getName(), LWL.class);
	}
	/**
	 * Resolve file from file system path or classpath on a best effort basis.
	 * @param path
	 * @return
	 * @throws FileNotFoundException
	 */
	public static File resolvePath(String path) throws FileNotFoundException 
	{
		File f;
		try 
		{
			f = ResourceUtils.getFile(path);
			if(f.exists())
				return f;
			else
				throw new FileNotFoundException();
		} 
		catch (Exception e) {
			try 
			{
				f = ResourceUtils.getFile(ResourceUtils.CLASSPATH_URL_PREFIX+path);
				if(f.exists())
					return f;
				else
					throw new FileNotFoundException();
			} catch (Exception e1) {
				f = ResourceUtils.getFile(ResourceUtils.FILE_URL_PREFIX+path);
				if(f.exists())
					return f;
				else
					throw new FileNotFoundException(path);
			}
		}
	}
	public static Path renameFileExtn(File f, String ext) throws IOException
	{
		Path p = f.toPath();
		String rename = f.getName();
		rename = rename.substring(0, rename.indexOf('.')) + "." + ext;
		return Files.move(p, p.resolveSibling(rename), StandardCopyOption.ATOMIC_MOVE);
	}
	/**
	 * 
	 * @param str
	 * @param len truncate to length
	 * @param rpad right pad with pattern, after len chars
	 * @return
	 */
	public static String truncate(String str, int len, String rpad)
	{
		if(StringUtils.hasText(str))
		{
			if(str.length() > len)
			{
				String sub = str.substring(0, len);
				return sub + rpad;
			}
		}
		return str;
	}
	public static String toXml(Object o)
	{
		return XSTREAM.toXML(o);
	}
	public static String toPrettyXml(Object o)
	{
		return prettyFormatXml(toXml(o), 4);
	}
	public static Object fromXml(String xml)
	{
		return XSTREAM.fromXML(xml);
	}
	static {
		initXStream();
	}
	
	public static final String WEKA_MODEL_PERSIST_MAP = "WEKA_MODEL_ENS";
	public static final String WEKA_COMMUNICATION_TOPIC = "WEKA_INTERCOMM";

	public static String toTimeElapsedString(long duration)
	{
		StringBuilder s = new StringBuilder();
		long t = TimeUnit.MILLISECONDS.toMinutes(duration);
		s.append(t).append(" min ");
		duration -= TimeUnit.MINUTES.toMillis(t);
		t = TimeUnit.MILLISECONDS.toSeconds(duration);
		s.append(t).append(" sec ");
		duration -= TimeUnit.SECONDS.toMillis(t);
		s.append(duration).append(" ms");
		return s.toString();
		
	}
	public static void main(String[] args) {
		System.out.println(toTimeElapsedString(61101));
	}
	/**
	 * Formats a xml string
	 * 
	 * @param input
	 * @param indent
	 * @return
	 */
	public static String prettyFormatXml(String input, int indent) {
		try 
		{
			Source xmlInput = new StreamSource(new StringReader(input));
			StringWriter stringWriter = new StringWriter();
			StreamResult xmlOutput = new StreamResult(stringWriter);
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			transformerFactory.setAttribute("indent-number", indent);
			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.transform(xmlInput, xmlOutput);

			return xmlOutput.getWriter().toString();
		} catch (Exception e) {
			System.out.println("<WARNING_log_suppressed> "+e);
			//e.printStackTrace();
		}
		return input;
	}

}
