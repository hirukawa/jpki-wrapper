package net.osdn.jpki.wrapper;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;

public interface JpkiWrapper {

    public void setApplicationName(String name);

    public void setApplicationVersion(String version);

    public void addSignature(OutputStream output, PDDocument document, String name, String reason, Date date, String location, String contact, SignatureOptions options) throws IOException;
}
