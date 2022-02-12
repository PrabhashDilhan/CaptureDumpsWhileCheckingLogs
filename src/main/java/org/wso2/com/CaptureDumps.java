package org.wso2.com;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.Scanner;

public class CaptureDumps {

    public static void main(String[] args) throws InterruptedException, IOException {

        Scanner scanner = new Scanner(System.in);

        System.out.print("Troubleshooting dump capturing started\n");

        System.out.print("Enter the absolute path to the WSO2 product home : ");
        String wso2home = scanner.next();

        System.out.print("Enter the time gap that should used to run the grep command in the logs : ");
        int timeGap = Integer.parseInt(scanner.next());

        System.out.print("Enter the keyword to use in the grep : ");
        String grepKey = scanner.next();

        System.out.print("How many occurance of the keyword should be present in the log snippet to start capturing the dumps : ");
        int occurance = Integer.parseInt(scanner.next());

        System.out.print("Do you want to capture a JFR dump : (yes=1, no=0)");
        int jfr = Integer.parseInt(scanner.next());
        int jfrduration = 0;
        if(jfr > 0){
            System.out.print("Enter the time duration to capture JFR in minutes : ");
            jfrduration = Integer.parseInt(scanner.next());
        }

        System.out.print("Do you want to capture a TCP dump : (yes=1, no=0)");
        int tcp = Integer.parseInt(scanner.next());
        int tcpduration = 0;
        if(tcp > 0){
            System.out.print("Enter the time duration to capture TCP in minutes : ");
            tcpduration = Integer.parseInt(scanner.next());
        }

        System.out.print("Do you want to capture a Thread dump : (yes=1, no=0)");
        int thread = Integer.parseInt(scanner.next());
        int threadcount = 0;
        int threadduration = 0;
        if(thread > 0){
            System.out.print("Enter the thread dumps count needs be captured : ");
            threadcount = Integer.parseInt(scanner.next());
            System.out.print("Enter the time gap in seconds to capture Thread dumps : ");
            threadduration = Integer.parseInt(scanner.next());
        }


        Timestamp startTime = new Timestamp(System.currentTimeMillis());
        int loop = 0;

        while(loop>0){
            System.out.println("Starting the process");
            Thread.sleep(timeGap*1000);
            Timestamp t1 = new Timestamp(System.currentTimeMillis());
            String start = startTime.toString().split(".")[0];
            String end = t1.toString().split(".")[0];
            runAWK(wso2home,start,end);
            if(runGrepCount(wso2home,grepKey)> occurance){

                if(jfr>0 && jfrduration >0){
                    runJFR(wso2home,jfrduration);
                }else if(tcp>0 && tcpduration >0){
                    runTCPdump(wso2home,tcpduration);
                }else if(thread>0 && threadcount >0 && threadduration >0){
                    for (int i=1; i>=threadcount; i++){
                        System.out.println("Capturing the thread dump: "+i);
                        runThreaddump(wso2home,i);
                        System.out.println("sleeping the process for "+threadduration+" seconds.");
                        Thread.sleep(threadduration*1000);
                    }
                }
                System.out.println("Dump capturing completed, Exiting the process");
                loop++;
            }
            startTime = t1;
        }
    }

    private static void runAWK(String homedir, String start, String end) throws IOException {
        String n = "awk";

        String a = "/"+start+"/,/"+end+"/";
        String aaa = homedir+File.separator+"repository"+File.separator+"logs"+File.separator+"wso2carbon.log";
        CommandLine commandAWK = new CommandLine("awk");
        commandAWK.addArgument(a,false);
        commandAWK.addArgument(aaa,false);
        ByteArrayOutputStream bOutputStream = new ByteArrayOutputStream();
        DefaultExecutor exec = new DefaultExecutor();
        PumpStreamHandler streamHandler = new PumpStreamHandler(bOutputStream);
        exec.setStreamHandler(streamHandler);

        exec.execute(commandAWK);
        try(OutputStream outputStream = new FileOutputStream(homedir+File.separator+"awkOut.txt")) {
            bOutputStream.writeTo(outputStream);
        }
    }

    private static int runGrepCount(String homedir,String grepK) throws IOException {
        CommandLine commandGep = new CommandLine("grep");
        commandGep.addArgument("-ir",false);
        commandGep.addArgument(grepK,true);
        String filepath = homedir+File.separator+"awkOut.txt";
        commandGep.addArguments(filepath,false);
        ByteArrayOutputStream outputStream2 = new ByteArrayOutputStream();
        DefaultExecutor exec2 = new DefaultExecutor();
        PumpStreamHandler streamHandler2 = new PumpStreamHandler(outputStream2);
        exec2.setStreamHandler(streamHandler2);

        exec2.execute(commandGep);
        try(OutputStream outputStream = new FileOutputStream(homedir+File.separator+"grepOut.txt")) {
            outputStream2.writeTo(outputStream);
        }

        CommandLine commandWC = new CommandLine("wc");
        commandWC.addArgument("-l",true);
        String grepOut = homedir+File.separator+"grepOut.txt";
        commandWC.addArguments(grepOut,true);
        ByteArrayOutputStream outputStream22 = new ByteArrayOutputStream();
        DefaultExecutor exec22 = new DefaultExecutor();
        PumpStreamHandler streamHandler22 = new PumpStreamHandler(outputStream22);
        exec22.setStreamHandler(streamHandler22);

        exec22.execute(commandWC);
        String[] ar = outputStream22.toString().trim().split(" ");
        System.out.println(ar[0]);

        int lin = Integer.parseInt(ar[0]);

        return lin;
    }



    private static void runJFR(String homedir, int jfrDuration) throws IOException {
            String data = new String(Files.readAllBytes(Paths.get(homedir+File.separator+"wso2carbon.pid")));
            System.out.println(data);
            CommandLine jfr = new CommandLine("jcmd");
            jfr.addArgument(data,false);
            jfr.addArgument("VM.unlock_commercial_features",false);
            DefaultExecutor exec2jfr = new DefaultExecutor();

            if(exec2jfr.execute(jfr) <1){
                CommandLine jfr2 = new CommandLine("jcmd");
                jfr2.addArgument(data,false);
                jfr2.addArgument("JFR.start",false);
                jfr2.addArgument("name=WSO2Process_jfr",false);
                String duration = "duration="+jfrDuration+"m";
                jfr2.addArgument(duration,false);
                jfr2.addArgument("filename=wso2_profiling.jfr",false);
                DefaultExecutor exec2jfr2 = new DefaultExecutor();
                exec2jfr2.execute(jfr2);
            }
    }

    private static void runTCPdump(String homedir, int tcpDuration) throws IOException {
            CommandLine tcp = new CommandLine("timeout");
            String duration = tcpDuration+"m";
            tcp.addArgument(duration,false);
            tcp.addArgument("tcpdump",false);
            tcp.addArgument("-i",false);
            tcp.addArgument("eny",false);
            tcp.addArgument("-w",false);
            tcp.addArgument("tcpdumpFile.pcap",false);
            DefaultExecutor exec2jfr = new DefaultExecutor();
            exec2jfr.execute(tcp);
    }

    private static void runThreaddump(String homedir, int iter) throws IOException {
            String data = new String(Files.readAllBytes(Paths.get(homedir+File.separator+"wso2carbon.pid")));
            System.out.println(data);
            CommandLine jfr = new CommandLine("jstack");
            jfr.addArgument(data,false);
            jfr.addArgument(">",false);
            String filename = "Threaddump"+iter+".txt";
            jfr.addArgument(filename,false);
            DefaultExecutor exec2jfr = new DefaultExecutor();
            exec2jfr.execute(jfr);
    }
}
