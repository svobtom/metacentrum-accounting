package cz.cesnet.meta.pbs;

import cz.cesnet.meta.RefreshLoader;
import cz.cesnet.meta.pbscache.PbsCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 *
 * @author Martin Kuba makub@ics.muni.cz
 * @version $Id: PbskyImpl.java,v 1.30 2014/04/11 08:38:08 makub Exp $
 */
@SuppressWarnings("Convert2streamapi")
public class PbskyImpl extends RefreshLoader implements Pbsky {

    final static Logger log = LoggerFactory.getLogger(PbskyImpl.class);

    private List<PbsServerConfig> pbsServers;
    private PbsCache pbsCache;
    private PbsConnector pbsConnector;

    private List<PBS> pbsky;
    private Map<String, Queue> queuesMap;
    private List<Job> allJobs;
    private JobsInfo jobsInfo;

    public void setPbsServers(List<PbsServerConfig> pbsServers) {
        this.pbsServers = pbsServers;
    }

    public void setPbsCache(PbsCache pbsCache) {
        this.pbsCache = pbsCache;
    }

    public void setPbsConnector(PbsConnector pbsConnector) {
        this.pbsConnector = pbsConnector;
    }

    public PbskyImpl() {
    }

    @Override
    public List<PBS> getPbsky() {
        //obsahuje checkLoad(), takze neni potreba tam, kde se volaji getPbsky()
        checkLoad();
        return pbsky;
    }

    private static PBS findPBSForServer(PbsServerConfig server, List<PBS> pbsList) {
        if(pbsList==null) return null;
        for (PBS p : pbsList) {
            if (p.getServerConfig().equals(server)) {
                return p;
            }
        }
        return null;
    }

    @Override
    protected void load() {
        try {
            List<PBS> pbskyNew = new ArrayList<>();
            Map<String, Queue> queuesMapNew = new HashMap<>();
            List<PBS> oldPbsky = new ArrayList<>();
            //nacti vsechno cerstve. pokud to jde, jinak si nech stara data
            for (PbsServerConfig server : pbsServers) {
                PBS oldData = findPBSForServer(server, pbsky);
                try {
                    PBS pbs = pbsConnector.loadData(server);
                    pbs.uprav();
                    pbskyNew.add(pbs);
                    log.debug("got new data "+pbs);
                    if(oldData!=null) {
                        oldPbsky.add(oldData);
                        log.debug("marked old data for clearing "+oldData);
                    }
                } catch (RuntimeException ex) {
                    log.error("Cannot load PBS data from server " + server.getHost() + ", reason: " + ex.getMessage());
                    //keep old data for that server
                    if (oldData != null) {
                        log.warn("keeping old data " + pbsky.toString());
                        pbskyNew.add(oldData);
                    } else {
                        log.warn("could not find old PBS data for server " + server.getHost());
                    }
                }

            }
            //predpripravit nova data
            for (PBS pbs : pbskyNew) {
                for (Queue q : pbs.getQueues().values()) {
                    queuesMapNew.put(q.getName(), q);
                }
                for (Node node : pbs.getNodesByName()) {
                    if(node.getPbs().isTorque()) {
                        node.setScratch(pbsCache.getScratchForNode(node));
                    } else {
                        node.setScratchPBSPro();
                    }
                    node.setGpuJobMap(pbsCache.getGpuAlloc(node));
                }
            }
            //vsechno se povedlo, mame nova data
            //prirazeni novych dat
            Map<String, Queue> oldQueuesMap = queuesMap;
            synchronized (this) {
                pbsky = pbskyNew;
                queuesMap = queuesMapNew;
                allJobs = null;
                jobsInfo = null;
            }
            //pomoc Garbage Collectoru
            if (oldQueuesMap != null) {
                oldQueuesMap.clear();
            }
            new Uklizec(oldPbsky).start();
        } catch (RuntimeException ex) {
            log.error("Cannot load PBS", ex);
        }

    }


    @Override
    synchronized public JobsInfo getJobsInfo() {
        if (jobsInfo == null) {
            jobsInfo = new JobsInfo(getAllJobs());
        }
        return jobsInfo;
    }

    private synchronized List<Job> getAllJobs() {
        List<PBS> list = getPbsky();
        if (allJobs == null) {
            int jobCount = 0;
            for (PBS pbs : list) {
                jobCount += pbs.getJobsById().size();
            }
            ArrayList<Job> jobs = new ArrayList<>(jobCount);
            for (PBS pbs : list) {
                jobs.addAll(pbs.getJobsById());
            }
            allJobs = jobs;
        }
        return allJobs;
    }

    @Override
    public Queue getQueueByName(String queueName) {
        checkLoad();
        return queuesMap.get(queueName);
    }

    @Override
    public Job getJobByName(String jobName) {
        for (PBS pbs : getPbsky()) {
            Job job = pbs.getJobs().get(jobName);
            if (job != null) return job;
        }
        return null;
    }

    @Override
    public List<Job> getSortedJobs(JobsSortOrder poradi) {
        checkLoad();
        List<Job> jobs = getAllJobs();
        if (poradi != JobsSortOrder.Id) {
            jobs = new ArrayList<>(jobs);
            switch (poradi) {
                case CPU:
                    Collections.sort(jobs, jobCPUComparator);
                    break;
                case CPUTime:
                    Collections.sort(jobs, jobCPUTimeUsedComparator);
                    break;
                case Name:
                    Collections.sort(jobs, jobNameComparator);
                    break;
                case Queue:
                    Collections.sort(jobs, jobQueueComparator);
                    break;
                case Ctime:
                    Collections.sort(jobs, jobCtimeComparator);
                    break;
                case ReservedMemTotal:
                    Collections.sort(jobs, jobReservedMemTotalComparator);
                    break;
                case UsedMem:
                    Collections.sort(jobs, jobUsedMemComparator);
                    break;
                case User:
                    Collections.sort(jobs, jobUserComparator);
                    break;
                case WallTime:
                    Collections.sort(jobs, jobWallTimeUsedComparator);
                    break;
                case State:
                    Collections.sort(jobs, jobStateComparator);
                    break;
            }
        }
        return jobs;
    }

    @Override
    public List<Node> getAllNodes() {
        int nodeCount = 0;
        List<PBS> list = getPbsky();
        for (PBS pbs : list) {
            nodeCount += pbs.getNodesByName().size();
        }
        ArrayList<Node> nodes = new ArrayList<>(nodeCount);
        for (PBS pbs : list) {
            nodes.addAll(pbs.getNodesByName());
        }
        Collections.sort(nodes, nodesNameComparator);
        return nodes;
    }

    private static User getUserByName(String userName, List<PBS> list) {
        User u = null;
        for (PBS pbs : list) {
            User u2 = pbs.getUsersMap().get(userName);
            if (u2 != null) {
                u = (u == null ? u2 : new User(u, u2));
            }
        }
        return u;
    }

    public User getUserByName(String userName) {
        return getUserByName(userName, getPbsky());
    }

    @Override
    public List<Job> getUserJobs(String userName, JobsSortOrder sort) {
        ArrayList<Job> jobs = new ArrayList<>();
        for (Job job : this.getSortedJobs(sort)) {
            if (job.getUser().equals(userName)) {
                jobs.add(job);
            }
        }
        return jobs;
    }

    @Override
    public Node getNodeByName(String nodeName) {
        for (PBS pbs : getPbsky()) {
            Node node = pbs.getNodes().get(nodeName);
            if (node != null) return node;
        }
        return null;
    }

    @Override
    public Node getNodeByFQDN(String fqdn) {
        for (PBS pbs : getPbsky()) {
            Node node = pbs.getFqdnToNodeMap().get(fqdn);
            if (node != null) return node;
        }
        return null;
    }

    @Override
    public int getJobsQueuedCount() {
        int total = 0;
        for (PBS pbs : getPbsky()) {
            total += pbs.getJobsQueuedCount();
        }
        return total;
    }

    private static Set<String> getUserNames(List<PBS> list) {
        Set<String> userNames = new TreeSet<>();
        for (PBS pbs : list) {
            userNames.addAll(pbs.getUsersMap().keySet());
        }
        return userNames;
    }

    @Override
    public Set<String> getUserNames() {
        return getUserNames(getPbsky());
    }


    @Override
    public List<User> getSortedUsers(UsersSortOrder usersSortOrder) {
        //spojit udaje ze vsech PBSek
        List<PBS> list = getPbsky();
        List<User> users = new ArrayList<>();
        for (String userName : getUserNames(list)) {
            users.add(getUserByName(userName, list));
        }
        //seradit
        switch (usersSortOrder) {
            case name:
                Collections.sort(users, userNameComparator);
                break;
            case jobsTotal:
                Collections.sort(users, userJobsTotalComparator);
                break;
            case jobsStateQ:
                Collections.sort(users, userJobsStateQComparator);
                break;
            case jobsStateR:
                Collections.sort(users, userJobsStateRComparator);
                break;
            case jobsStateC:
                Collections.sort(users, userJobsStateCComparator);
                break;
            case jobsOther:
                Collections.sort(users, userJobsOtherComparator);
                break;
            case cpusTotal:
                Collections.sort(users, userCpusTotalComparator);
                break;
            case cpusStateQ:
                Collections.sort(users, userCpusStateQComparator);
                break;
            case cpusStateR:
                Collections.sort(users, userCpusStateRComparator);
                break;
            case cpusStateC:
                Collections.sort(users, userCpusStateCComparator);
                break;
            case cpusOther:
                Collections.sort(users, userCpusOtherComparator);
                break;
            case fairshare:
                //neni zatim podle ceho tridit
                break;
            default:
                throw new RuntimeException("unknow choice usersSortOrder=" + usersSortOrder);
        }
        return users;
    }

    @SuppressWarnings("Convert2streamapi")
    @Override
    public List<TextWithCount> getReasonsForJobsQueued() {
        HashMap<String, Integer> pocitadla = new HashMap<>();
        for (PBS pbs : getPbsky()) {
            for (Job job : pbs.getJobsById()) {
                if ("Q".equals(job.getState())) {
                    String duvod = job.getComment();
                    Integer count = pocitadla.get(duvod);
                    if (count == null) {
                        count = 0;
                    }
                    pocitadla.put(duvod, count + 1);
                }
            }
        }
        List<TextWithCount> duvody = new ArrayList<>(pocitadla.size());
        for (String duvod : pocitadla.keySet()) {
            duvody.add(new TextWithCount(duvod, pocitadla.get(duvod)));
        }
        Collections.sort(duvody);
        return duvody;
    }

    private static class Uklizec extends Thread {
        private final List<PBS> pbsky_old;

        private Uklizec(List<PBS> pbsky) {
            super("uklizec");
            this.pbsky_old = pbsky;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(90000);
            } catch (InterruptedException e) {
                log.error("problem v cekani", e);
            }
            for (PBS pbs : pbsky_old) {
                pbs.clear();
            }
            pbsky_old.clear();
        }
    }


    //comparators for array sorting
    static final Comparator<Queue> queuesPriorityComparator = (o1, o2) -> o2.getPriority() - o1.getPriority();

    static Comparator<Node> nodesNameComparator = (h1, h2) -> {
        String h1clustName = h1.getClusterName();
        if (h1clustName == null) {
            log.error("Node h1=" + h1 + " has no clusterName");
            throw new IllegalArgumentException("node " + h1.getName() + " has no clusterName");
        }
        if (!h1clustName.equals(h2.getClusterName())) {
            return h1.getClusterName().compareTo(h2.getClusterName());
        } else {
            int diffNumInCluster = h1.getNumInCluster() - h2.getNumInCluster();
            if (diffNumInCluster != 0) {
                return diffNumInCluster;
            } else {
                return h1.getVirtNum() - h2.getVirtNum();
            }
        }
    };

    static final Comparator<Job> jobsIdComparator = (j1, j2) -> {
        int diff = j1.getIdNum() - j2.getIdNum();
        return (diff == 0) ? j1.getIdSubNum() - j2.getIdSubNum() : diff;
    };


    private static Comparator<Job> jobCPUComparator = (o1, o2) -> o2.getNoOfUsedCPU() - o1.getNoOfUsedCPU();

    private static Comparator<Job> jobNameComparator = (o1, o2) -> o1.getJobName().compareTo(o2.getJobName());

    private static Comparator<Job> jobUserComparator = (o1, o2) -> o1.getUser().compareTo(o2.getUser());

    private static Comparator<Job> jobCPUTimeUsedComparator = (o1, o2) -> (int) (o2.getCPUTimeUsedSec() - o1.getCPUTimeUsedSec());

    private static Comparator<Job> jobWallTimeUsedComparator = (o1, o2) -> o1.getWalltimeUsed().compareTo(o2.getWalltimeUsed());

    private static Comparator<Job> jobStateComparator = (o1, o2) -> JobState.valueOf(o1.getState()).compareTo(JobState.valueOf(o2.getState()));

    private static Comparator<Job> jobQueueComparator = (o1, o2) -> o1.getQueueName().compareTo(o2.getQueueName());

    private static Comparator<Job> jobCtimeComparator = (o1, o2) -> o1.getTimeCreated().compareTo(o2.getTimeCreated());

    private static Comparator<Job> jobUsedMemComparator = (o1, o2) -> {
        long l = o2.getUsedMemoryNum() - o1.getUsedMemoryNum();
        return (l > 0 ? 1 : (l < 0 ? -1 : 0));
    };
    private static Comparator<Job> jobReservedMemTotalComparator = (o1, o2) -> {
        long l = o2.getReservedMemoryTotalNum() - o1.getReservedMemoryTotalNum();
        return (l > 0 ? 1 : (l < 0 ? -1 : 0));
    };

    private static Comparator<User> userNameComparator = (u1, u2) -> u1.getName().compareTo(u2.getName());

    private static Comparator<User> userJobsTotalComparator = (u1, u2) -> u2.getJobsTotal() - u1.getJobsTotal();

    private static Comparator<User> userJobsStateQComparator = (u1, u2) -> u2.getJobsStateQ() - u1.getJobsStateQ();

    private static Comparator<User> userJobsStateRComparator = (u1, u2) -> u2.getJobsStateR() - u1.getJobsStateR();

    private static Comparator<User> userJobsStateCComparator = (u1, u2) -> u2.getJobsStateC() - u1.getJobsStateC();

    private static Comparator<User> userJobsOtherComparator = (u1, u2) -> u2.getJobsOther() - u1.getJobsOther();

    private static Comparator<User> userCpusTotalComparator = (u1, u2) -> u2.getCpusTotal() - u1.getCpusTotal();

    private static Comparator<User> userCpusStateQComparator = (u1, u2) -> u2.getCpusStateQ() - u1.getCpusStateQ();

    private static Comparator<User> userCpusStateRComparator = (u1, u2) -> u2.getCpusStateR() - u1.getCpusStateR();

    private static Comparator<User> userCpusStateCComparator = (u1, u2) -> u2.getCpusStateC() - u1.getCpusStateC();

    private static Comparator<User> userCpusOtherComparator = (u1, u2) -> u2.getCpusOther() - u1.getCpusOther();


}
