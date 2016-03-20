package webserver;

import in2011.http.Request;
import in2011.http.Response;
import in2011.http.MessageFormatException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebServer {

    final private int port;
    final private String rootDir;
    int count = 0;

    public WebServer(int port, String rootDir) {
        this.port = 1091;
        this.rootDir = "/Users/BomBum/Dropbox/Uni/Comp Sci/Year 2/Spring/Network/NetsOpsCoursework/NetsOpsCoursework/WebServer/data/";
    }

    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        while (true) {
            Socket conn = serverSocket.accept();
            new Thread(new Finder(conn)).start();
            System.out.println("Done");
        }
    }

    public static void main(String[] args) throws IOException {

        String usage = "Usage: java webserver.WebServer <port-number> <root-dir>";
        if (args.length != 2) {
            throw new Error(usage);
        }
        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new Error(usage + "\n" + "<port-number> must be an integer");
        }
        String rootDir = args[1];
        WebServer server = new WebServer(port, rootDir);
        server.start();
    }

    public class Finder implements Runnable {

        private Socket conn;
        protected Thread runningThread = null;
        Date date = new Date();

        public Finder(Socket conn) {
            this.conn = conn;
        }

        public void run() {

            InputStream is = null;
            try {
                //Counts how many connections to the server are 
                count++;
                System.out.println(count);
                System.out.println("Accepting new requests on port " + port);
                is = conn.getInputStream();
                OutputStream os = conn.getOutputStream();
                Request req = Request.parse(is);
                System.out.println(req);
                String uri = req.getURI();
                //Advanced feature 2.5 reads %hex %hex escapes in URIs
                String u = URLDecoder.decode(uri, "ASCII");
                Path p = Paths.get(rootDir + u);
                p = p.toAbsolutePath().normalize();
                File file = new File(p.toString());
                System.out.println(p);
                long modifiedMili = 0;
                //GET METHOD
                if (req.getMethod().equals("GET")) {
                    //Checks if the file exists and that the GET request is not a directory
                    if (p.toFile().exists() && !file.isDirectory()) {
                        //Checks if in the request header field for an If-Modified-Since.
                        if (req.getHeaderFieldValue("If-Modified-Since") == null) {

                            System.out.println(u);
                            //Sets a string as the file's content type for use in the Header
                            String fType = Files.probeContentType(p);
                            //Sets a long as the file's content length for use in the Header
                            long fLength = file.length();
                            //Forms the date format for the last modified header
                            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
                            
                            Response head = new Response(200);
                            head.addHeaderField("Content-Type: ", fType);
                            head.addHeaderField("Content-Length: ", Long.toString(fLength));
                            head.addHeaderField("Last Modified: ", sdf.format(file.lastModified()).toString());

                            byte[] b = Files.readAllBytes(p);
                            head.write(os);
                            os.write(b);
                            //If there is an If-Modified-Since Get request header
                        } else {
                            
                            //Gets the If-Modified-Since request date
                            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
                            Date modiDate = null;
                            try {
                                modiDate = sdf.parse(req.getHeaderFieldValue("If-Modified-Since"));
                            } catch (ParseException ex) {
                                Logger.getLogger(WebServer.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            modifiedMili = modiDate.getTime();
                            //Compares If Modified Since with the file's last modified date
                            if (modifiedMili <= file.lastModified()) {
                                //Normal GET request if the If Modified Since date is older then the last modified date
                                System.out.println(u);
                                String fType = Files.probeContentType(p);
                                long fLength = file.length();
                                Response head = new Response(200);
                                head.addHeaderField("Content-Type: ", fType);
                                head.addHeaderField("Content-Length: ", Long.toString(fLength));
                                head.addHeaderField("Last Modified: ", sdf.format(file.lastModified()).toString());

                                byte[] b = Files.readAllBytes(p);
                                head.write(os);
                                os.write(b);
                                //If modified since is newer then last modified
                            } else {
                                //304 not modified sent
                                new Response(304).write(os);

                            }
                        }
                        //If none of the other conditions are met then the file does not exist and a 404 is sent
                    } else {
                        new Response(404).write(os);
                        os.close();
                    }
                    //HEAD METHOD
                } else if (req.getMethod().equals("HEAD")) {
                    //checks whether the file exists and sends the Header details
                    if (p.toFile().exists()) {

                        String fType = Files.probeContentType(p);
                        long fLength = file.length();
                        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");

                        Response head = new Response(200);
                        head.addHeaderField("Content-Type: ", fType);
                        head.addHeaderField("Content-Length: ", Long.toString(fLength));
                        head.addHeaderField("Last Modified: ", sdf.format(file.lastModified()).toString());
                        head.write(os);

                        conn.close();
                        //If the file doesn't exist then 404 is sent
                    } else {
                        new Response(404).write(os);
                        os.close();
                    }
                    //PUT METHOD
                } else if (req.getMethod().equals("PUT")) {
                    //Checks if the file exists and that it is not a directory
                    if (!Files.exists(p) && !file.isDirectory()) {
                        try {
                            //Creates a file at the specified location
                            file.getParentFile().mkdirs();
                            Files.createFile(p);
                            //Writes the input into the file
                            OutputStream fOs = Files.newOutputStream(p);
                            while (true) {
                                int b = is.read();

                                if (b == -1) {
                                    break;
                                }
                                //If the size exceeds 1024*1024 then 400 Bad request is sent
                                if (b > 1024 * 1024) {
                                    new Response(400).write(os);
                                    //If the file is smaller then 1024*1024 then no problems
                                } else if (b <= 1024 * 1024) {
                                    fOs.write(b);
                                }
                            }
                            //201 file was created
                            new Response(201).write(os);
                            fOs.close();
                        } catch (IOException ioe) {
                            new Response(400).write(os);
                        }
                        //Checks if it is a directory
                    } else if (file.isDirectory()) {
                        try {
                            //If it is then create the directory at the path
                            Files.createDirectories(p);
                        } catch (IOException ex) {
                            new Response(500).write(os);
                        }
                    } else {
                        new Response(403).write(os);
                    }

                } else {
                    new Response(501).write(os);
                    os.close();
                }
                is.close();
                count--;
            } catch (IOException ex) {
                Logger.getLogger(WebServer.class.getName()).log(Level.SEVERE, null, ex);
            } catch (MessageFormatException ex) {
                Logger.getLogger(WebServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }
}
