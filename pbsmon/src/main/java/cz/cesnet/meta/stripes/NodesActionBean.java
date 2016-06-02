package cz.cesnet.meta.stripes;

import cz.cesnet.meta.cloud.Cloud;
import cz.cesnet.meta.cloud.CloudPhysicalHost;
import cz.cesnet.meta.cloud.CloudVirtualHost;
import cz.cesnet.meta.pbs.Node;
import cz.cesnet.meta.pbscache.Mapping;
import cz.cesnet.meta.pbscache.PbsCache;
import cz.cesnet.meta.pbsmon.PbsmonUtils;
import cz.cesnet.meta.pbsmon.RozhodovacStavuStroju;
import cz.cesnet.meta.perun.api.*;
import cz.cesnet.meta.storages.Storages;
import cz.cesnet.meta.storages.StoragesInfo;
import net.sourceforge.stripes.action.DefaultHandler;
import net.sourceforge.stripes.action.ForwardResolution;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.UrlBinding;
import net.sourceforge.stripes.integration.spring.SpringBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@UrlBinding("/nodes/{$event}")
public class NodesActionBean extends BaseActionBean {

    final static Logger log = LoggerFactory.getLogger(NodesActionBean.class);

    @SpringBean("perun")
    protected Perun perun;

    @SpringBean("diskArrays")
    protected Storages diskArraysLoader;

    @SpringBean("hsms")
    protected Storages hsmsLoader;

    StoragesInfo storagesInfo;
    StoragesInfo hsmInfo;
    List<VypocetniCentrum> centra;
    List<Stroj> zbyle;
    Map<String, Integer> cpuMap;
    int jobsQueuedCount;
    int maxVirtual = 0;
    List<Stroj> fyzicke;
    Mapping mapping;
    Map<String, Node> nodeMap;
    Map<String, CloudVirtualHost> fqdn2CloudVMMap;
    List<Node> gpuNodes;

    /**
     * Zobrazuje fyzické stroje.
     *
     * @return stránka nodes.jsp
     */
    @DefaultHandler
    public Resolution physical() {
        log.debug("physical({})", ctx.getRequest().getRemoteHost());
        FyzickeStroje fyzickeStroje = perun.getFyzickeStroje();
        centra = fyzickeStroje.getCentra();
        zbyle = fyzickeStroje.getZbyle();
        cpuMap = fyzickeStroje.getCpuMap();
        RozhodovacStavuStroju.rozhodniStavy(pbsky, perun.getVyhledavacFrontendu(),
                pbsCache.getMapping(), perun.getVyhledavacVyhrazenychStroju(), centra, cloud);
        jobsQueuedCount = pbsky.getJobsQueuedCount();
        //diskova pole
        storagesInfo = diskArraysLoader.getStoragesInfo();
        //HSMs
        hsmInfo = hsmsLoader.getStoragesInfo();
        //GPU
        gpuNodes = findGpuNodes();
        return new ForwardResolution("/nodes/nodes.jsp");
    }

    private  List<Node> findGpuNodes() {
        return centra.stream()
                .flatMap(centrum -> centrum.getZdroje().stream())
                .flatMap(zdroj -> zdroj.isCluster()?zdroj.getStroje().stream():Stream.of(zdroj.getStroj()))
                .flatMap(stroj -> PbsmonUtils.getPbsNodesForPhysicalMachine(stroj,pbsky, pbsCache, cloud).stream())
                .filter(Node::getHasGPU)
                .collect(Collectors.toList());
    }

    public StoragesInfo getStoragesInfo() {
        return storagesInfo;
    }

    public StoragesInfo getHsmInfo() {
        return hsmInfo;
    }

    public Map<String, Integer> getCpuMap() {
        return cpuMap;
    }

    public List<VypocetniCentrum> getCentra() {
        return centra;
    }

    public List<Stroj> getZbyle() {
        return zbyle;
    }

    public int getJobsQueuedCount() {
        return jobsQueuedCount;
    }

    public List<Node> getGpuNodes() {
        return gpuNodes;
    }

    public Resolution virtual() {
        log.debug("virtual()");
        List<Stroj> vsechnyStroje = this.perun.getMetacentroveStroje();
        fyzicke = new ArrayList<>(vsechnyStroje.size());
        //mapa z hostname PBS uzlu na PBS uzel
        nodeMap = new HashMap<>(vsechnyStroje.size());
        //mapovani z jmen virtualnich na jmena fyzickych a naopak
        mapping = makeUnifiedMapping(this.pbsCache, this.cloud);
        //mapa VM z cloudu
        List<CloudVirtualHost> virtualHosts = cloud.getVirtualHosts();
        fqdn2CloudVMMap = new HashMap<>(virtualHosts.size() * 2);
        for (CloudVirtualHost vm : virtualHosts) {
            fqdn2CloudVMMap.put(vm.getFqdn(), vm);
        }
        //pripravit
        for (Stroj s : vsechnyStroje) {
            String strojName = s.getName();
            //PBs uzel primo na fyzickem - nevirtualizovane nebo Magratea-cloudove
            Node pbsNode = pbsky.getNodeByName(strojName);
            if (pbsNode != null) nodeMap.put(strojName, pbsNode);
            // pro vsechny fyzicke vytahat virtualni s PBS uzly
            if (!s.isVirtual()) {
                fyzicke.add(s);
                List<String> virtNames = mapping.getPhysical2virtual().get(s.getName());
                if (virtNames != null) {
                    for (String virtName : virtNames) {
                        Node vn = pbsky.getNodeByName(virtName);
                        if (vn != null) {
                            nodeMap.put(vn.getName(), vn);
                        }
                    }
                }
            }
        }

        Collections.sort(fyzicke);


        maxVirtual = 0;
        for (List<String> list : mapping.getPhysical2virtual().values()) {
            int size = list.size();
            if (size > maxVirtual) maxVirtual = size;
        }

        //rozhodni stav podle virtualnich
        VyhledavacFrontendu frontendy = perun.getVyhledavacFrontendu();
        VyhledavacVyhrazenychStroju vyhrazene = perun.getVyhledavacVyhrazenychStroju();
        for (Stroj stroj : fyzicke) {
            RozhodovacStavuStroju.rozhodniStav(stroj, pbsky, pbsCache.getMapping(), frontendy, vyhrazene, cloud);
        }

        return new ForwardResolution("/nodes/mapping.jsp");
    }

    public static Mapping makeUnifiedMapping(PbsCache pbsCache, Cloud cloud) {
        Mapping m = new Mapping();
        Map<String, List<String>> physical2virtual = new HashMap<>();
        Map<String, String> virtual2physical = new HashMap<>();
        m.setPhysical2virtual(physical2virtual);
        m.setVirtual2physical(virtual2physical);
        //Magrathea
        Mapping magratheaMapping = pbsCache.getMapping();
        physical2virtual.putAll(magratheaMapping.getPhysical2virtual());
        virtual2physical.putAll(magratheaMapping.getVirtual2physical());
        //OpenNebula
        Map<String, List<CloudVirtualHost>> hostName2VirtualHostsMap = cloud.getHostName2VirtualHostsMap();
        for (CloudPhysicalHost host : cloud.getPhysicalHosts()) {
            List<String> vmFqdns = new ArrayList<>();
            for (CloudVirtualHost vm : hostName2VirtualHostsMap.get(host.getName())) {
                vmFqdns.add(vm.getFqdn());
                virtual2physical.put(vm.getFqdn(), host.getHostname());
            }
            physical2virtual.put(host.getHostname(), vmFqdns);
        }
        return m;
    }

    public List<Stroj> getFyzicke() {
        return fyzicke;
    }

    public Mapping getMapping() {
        return mapping;
    }

    public int getMaxVirtual() {
        return maxVirtual;
    }

//    public Date getPbsTimeLoaded() {
//        return pbsky.getTimeLoaded();
//    }

    public Map<String, Node> getNodeMap() {
        return nodeMap;
    }

    public Map<String, CloudVirtualHost> getFqdn2CloudVMMap() {
        return fqdn2CloudVMMap;
    }
}