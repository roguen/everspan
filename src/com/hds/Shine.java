package com.hds;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

import com.hds.auto.commons.util.DataStreamer;
import com.hds.hcp.sdk.gateway.HcpHttpGateway;
import com.hds.hcp.tools.shine.Counter;
import com.hds.hcp.tools.shine.Gateway;
import com.hds.hcp.tools.shine.IdenticalJobBuilder;
import com.hds.hcp.tools.shine.ImpatientWorker;
import com.hds.hcp.tools.shine.Ingestion;
import com.hds.hcp.tools.shine.Job;
import com.hds.hcp.tools.shine.Logger;
import com.hds.hcp.tools.shine.Utils;
import com.hds.hcp.tools.shine.Worker;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;

public class Shine {

    private Options options = new Options();

    private Options getOptions() {
        return this.options;
    }

    public Shine() {
        this.options.addOption(this.createOption("c", "cluster", "cluster", 1, "Specify cluster host name", true));
        this.options.addOption(this.createOption("t", "tenant", "tenant", 1, "Specify tenant name", true));
        this.options.addOption(this.createOption("n", "namespace", "namespace", 1, "Specify namespace name", true));
        this.options.addOption(this.createOption("u", "username", "username", 1, "Specify username", true));
        this.options.addOption(this.createOption("p", "password", "password", 1, "Specify password", true));
        this.options.addOption(this.createOption("o", "operation", "operation> <file", 2, "Specify REST operation: PUT, GET, HEAD", true));
        this.options.addOption(this.createOption("k", "threads", "threads", 1, "Specify number of threads (Max: 512)", false));
        this.options.addOption(this.createOption("f", "file", "count> <file", 2, "Specify file count and size (in bytes)", false));
        this.options.addOption(this.createOption("d", "directory", "count> <depth> <width", 3, "Specify directory count, depth, and width", false));
        this.options.addOption(this.createOption("dm", "defaultMetadata", "Attach default metadata to ingested objects", false));
        this.options.addOption(this.createOption("cm", "customMetadata", "file", 1, "Attach custom metadata to ingested objects", false));
        this.options.addOption(this.createOption("lb", "loadBalance", "ipAddresses", 1, "Load balances objects into the given IP addresses", false));
        this.options.addOption(this.createOption("ssl", "secure", "Encrypt HTTP connection with SSL (HTTPS)", false));
        this.options.addOption(this.createOption("silent", "silentLogging", "Disable logging to console output. Saves JSON result to current directory", false));
        this.options.addOption(this.createOption("r", "retry", "Send an identical request if HCP takes too long to respond", false));
    }

    private Option createOption(String shortOpt, String longOpt, String description, boolean required) {
        OptionBuilder.withLongOpt(longOpt);
        OptionBuilder.withDescription(description);
        OptionBuilder.isRequired(required);
        return OptionBuilder.create(shortOpt);
    }

    private Option createOption(String shortOpt, String longOpt, String argName, int argNum, String description, boolean required) {
        OptionBuilder.withLongOpt(longOpt);
        OptionBuilder.withArgName(argName);
        OptionBuilder.hasArgs(argNum);
        OptionBuilder.withDescription(description);
        OptionBuilder.isRequired(required);
        return OptionBuilder.create(shortOpt);
    }

    public static void main(String[] args) throws Exception {
        Shine shine = new Shine();
        Counter counter = new Counter();

        System.out.println("This is the new version of the shine program");
        try {
            counter.startExecutionTimer();
            GnuParser e = new GnuParser();
            CommandLine line = e.parse(shine.getOptions(), args);
            Logger log = new Logger(line.hasOption("silent"));
            String cluster = line.getOptionValue("c");
            String tenant = line.getOptionValue("t");
            String namespace = line.getOptionValue("n");
            String username = line.getOptionValue("u");
            String password = line.getOptionValue("p");
            HcpHttpGateway.HttpProtocol protocol = line.hasOption("ssl")? HcpHttpGateway.HttpProtocol.HTTPS: HcpHttpGateway.HttpProtocol.HTTP;
            Gateway gateway;
            if(line.hasOption("lb")) {
                List threads = Arrays.asList(line.getOptionValue("lb").split(","));
                gateway = new Gateway(cluster, tenant, namespace, username, password, protocol, threads);
            } else {
                gateway = new Gateway(cluster, tenant, namespace, username, password, protocol);
            }

            int threads1 = line.hasOption("k")?Integer.parseInt(line.getOptionValue("k")):512;
            if(threads1 > 512) {
                throw new Exception("Thread option (-k) must not be greater than 512");
            }

            ExecutorService executor = Executors.newFixedThreadPool(threads1);
            String[] operationLine = line.getOptionValues("o");
            String operation = operationLine[0];
            Path operationPath = Paths.get(operationLine[1], new String[0]);
            if(operation.equals("PUT") && !line.hasOption("d") && !line.hasOption("f")) {
                throw new Exception("-d and -f options are required for PUT operation");
            }

            boolean hasRetry = line.hasOption('r');
            byte hasCustomMetadata = -1;
            switch(operation.hashCode()) {
                case 70454:
                    if(operation.equals("GET")) {
                        hasCustomMetadata = 1;
                    }
                    break;
                case 79599:
                    if(operation.equals("PUT")) {
                        hasCustomMetadata = 0;
                    }
                    break;
                case 2213344:
                    if(operation.equals("HEAD")) {
                        hasCustomMetadata = 2;
                    }
            }

            List resources;
            Ingestion ingestion;
            switch(hasCustomMetadata) {
                case 0:
                    String[] hasNoMetadata = line.getOptionValues("d");
                    if(hasNoMetadata.length != 3) {
                        throw new Exception("Missing required arguments to -d option");
                    }

                    int defaultMetadata = Integer.parseInt(hasNoMetadata[0]);
                    int customMetadata = Integer.parseInt(hasNoMetadata[1]);
                    int workers = Integer.parseInt(hasNoMetadata[2]);
                    List dataFileSize = Utils.createDirSet(defaultMetadata, customMetadata, customMetadata);
                    String[] fileOptions = line.getOptionValues("f");
                    if(fileOptions.length != 2) {
                        throw new Exception("Missing required argument to -f option");
                    }

                    int i$ = Integer.parseInt(fileOptions[0]);
                    int worker = Integer.parseInt(fileOptions[1]);
                    resources = Utils.createWorkSet(dataFileSize, (long)i$);
                    Files.write(operationPath, resources, Charset.defaultCharset(), new OpenOption[0]);
                    ingestion = new Ingestion(defaultMetadata, customMetadata, workers, i$, worker, resources);
                    counter.setOperationTotalCount(i$);
                    break;
                case 1:
                case 2:
                    resources = Files.readAllLines(operationPath, Charset.defaultCharset());
                    ingestion = new Ingestion(resources);
                    counter.setOperationTotalCount(ingestion.getResources().size());
                    break;
                default:
                    throw new Exception(String.format("Unrecognized operation type: %s", new Object[]{operation}));
            }

            boolean hasDefaultMetadata = line.hasOption("dm");
            boolean hasCustomMetadata1 = line.hasOption("cm");
            if(hasCustomMetadata1 && hasDefaultMetadata) {
                throw new Exception();
            }

            boolean hasNoMetadata1 = !hasDefaultMetadata && !hasCustomMetadata1;
            File defaultMetadata1 = hasDefaultMetadata?Utils.createDefaultXMLFile():null;
            File customMetadata1 = hasCustomMetadata1?Utils.createCustomXMLFile(Paths.get(line.getOptionValue("cm"), new String[0])):null;
            log.summary(cluster, tenant, namespace, username, password, protocol, threads1, operation, operationPath, ingestion, hasNoMetadata1, defaultMetadata1, customMetadata1);
            log.info(String.format("Preparing jobs for %s %s", new Object[]{protocol.toString().toUpperCase(), operation}));
            ArrayList workers1 = new ArrayList();
            long dataFileSize1 = (long)ingestion.getFileSize();
            Iterator i$1 = ingestion.getResources().iterator();

            while(i$1.hasNext()) {
                String worker1 = (String)i$1.next();
                DataStreamer data = new DataStreamer(dataFileSize1);
                if(hasRetry) {
                    IdenticalJobBuilder job = new IdenticalJobBuilder(worker1, dataFileSize1, operation);
                    if(hasDefaultMetadata) {
                        job.useDefaultMetadata(defaultMetadata1);
                    } else if(hasCustomMetadata1) {
                        job.useCustomMetadata(customMetadata1);
                    } else {
                        job.useNoMetadata();
                    }

                    workers1.add(new ImpatientWorker(gateway, job, counter, log));
                } else {
                    Job job1;
                    if(hasNoMetadata1) {
                        job1 = new Job(worker1, operation, data, dataFileSize1);
                    } else if(hasDefaultMetadata) {
                        job1 = new Job(worker1, operation, data, dataFileSize1, defaultMetadata1, false);
                    } else {
                        job1 = new Job(worker1, operation, data, dataFileSize1, customMetadata1, true);
                    }

                    workers1.add(new Worker(gateway, job1, counter, log));
                }
            }

            counter.startOperationTimer();
            log.info(String.format("Performing %s %s to %s.%s.%s\n", new Object[]{protocol.toString().toUpperCase(), operation, namespace, tenant, cluster}));
            i$1 = workers1.iterator();

            while(i$1.hasNext()) {
                Runnable worker2 = (Runnable)i$1.next();
                executor.execute(worker2);
            }

            executor.shutdown();

            while(!executor.isTerminated()) {
                log.progressUpdate(counter);
                Thread.sleep(100L);
            }

            log.progressUpdate(counter);
            counter.endOperationTimer();
            counter.endExecutionTimer();
            gateway.close();
            log.result(counter);
        } catch (Exception var33) {
            var33.printStackTrace();
            (new HelpFormatter()).printHelp("shine", shine.options);
        }

    }
}
