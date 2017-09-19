package net.osdn.jpki.wrapper.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Date;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDPropBuild;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDPropBuildDataDict;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;

import jp.go.jpki.appli.JPKICryptSignJNIException;
import jp.go.jpki.appli.JPKIUserCertBasicData;
import jp.go.jpki.appli.JPKIUserCertException;
import jp.go.jpki.appli.JPKIUserCertService;

public class JpkiWrapperInternal {
	
	public static final String DEFAULT_KEYWORD = ""
		+ "地方公共団体情報システム機構が運営する公的個人認証サービス ポータルサイト "
		+ "https://www.jpki.go.jp/ca/ca_rules3.html から"
		+ "署名用認証局の自己署名証明書（バイナリ形式）をダウンロードして"
		+ "信頼されたルート証明機関にインストールすることで署名を検証できるようになります。 ";
	
	private String applicationName;
	private String applicationVersion;
	
	public void setApplicationName(String name) {
		applicationName = name;
	}
	
	public void setApplicationVersion(String version) {
		applicationVersion = version;
	}
	
	public void addSignature(OutputStream output, PDDocument document, String name, String reason, Date date, String location, String contact, SignatureOptions options) throws IOException {
		try {
			addSignatureWithJNIException(output, document, name, reason, date, location, contact, options);
		} catch (JPKICryptSignJNIException e) {
			throw new IOException(String.format("!ErrorCode=%d,WinErrorCode=%d", e.getErrorCode(), e.getWinErrorCode()), e);
		} catch (JPKIUserCertException e) {
			throw new IOException(String.format("!ErrorCode=%d,WinErrorCode=%d", e.getErrorCode(), 0), e);
		}
	}
	
	private void addSignatureWithJNIException(OutputStream output, PDDocument document, String name, String reason, Date date, String location, String contact, SignatureOptions options) throws IOException, JPKICryptSignJNIException, JPKIUserCertException {
		String keyword = document.getDocumentInformation().getKeywords();
		if(keyword == null || keyword.length() == 0) {
			document.getDocumentInformation().setKeywords(DEFAULT_KEYWORD);
		}
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		document.save(out);
		document = PDDocument.load(out.toByteArray());
		
		JPKICryptSignProvider jpki = null;
		try {
			jpki = new JPKICryptSignProvider();

			if(name == null) {
				byte[] cert = jpki.getCertificate();
				JPKIUserCertService ucs = new JPKIUserCertService(cert);
				JPKIUserCertBasicData basicData = ucs.getBasicData();
				name = basicData.getName();
			}
			if(reason == null) {
				reason = name + " によって署名されています。";
			}
			if(date == null) {
				date = new Date();
			}
			
			PDSignature signature = new PDSignature();
			signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
			signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
			signature.setName(name);
			signature.setReason(reason);
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(date);
			signature.setSignDate(calendar);
			if(location != null) {
				signature.setLocation(location);
			}
			if(contact != null) {
				signature.setContactInfo(contact);
			}
			
			if(applicationName != null) {
				PDPropBuildDataDict dict = new PDPropBuildDataDict();
				dict.setName(applicationName);
				dict.setVersion(applicationVersion != null ? applicationVersion : "");
				dict.setTrustedMode(true);
				PDPropBuild propBuild = new PDPropBuild();
				propBuild.setPDPropBuildApp(dict);
				signature.setPropBuild(propBuild);
			}
			
			if(options != null) {
				document.addSignature(signature, new JPKISignatureInterface(jpki), options);
			} else {
				document.addSignature(signature, new JPKISignatureInterface(jpki));
			}
			document.saveIncremental(output);
		} finally {
			if(jpki != null) {
				try { jpki.close(); } catch(Exception e) {}
			}
		}
	}
}
