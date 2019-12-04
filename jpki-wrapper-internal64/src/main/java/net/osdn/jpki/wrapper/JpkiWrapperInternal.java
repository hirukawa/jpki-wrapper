package net.osdn.jpki.wrapper;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;

public interface JpkiWrapperInternal {

    void addSignature(OutputStream output, PDDocument document, String name, String reason, Date date, String location, String contact, SignatureOptions options, String applicationName, String applicationVersion) throws IOException;

}
