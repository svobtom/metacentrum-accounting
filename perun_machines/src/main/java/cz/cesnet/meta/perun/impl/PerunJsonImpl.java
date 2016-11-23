package cz.cesnet.meta.perun.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdicons.json.model.*;
import com.sdicons.json.parser.JSONParser;
import cz.cesnet.meta.perun.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Implementace čtoucí JSON data. Oproti předchozí PerunJdbcImpl nepotřebuje žádné on-line spojení na Peruna,
 * jen čte soubory ve formátu JSON vygenerované Perunem. Ze staréhoPeruna lze vygenerovat správně formátovaný
 * soubor třídou PerunV2Export.
 *
 * @author Martin Kuba makub@ics.muni.cz
 * @version $Id: PerunJsonImpl.java,v 1.5 2012/01/04 13:47:44 makub Exp $
 */
public class PerunJsonImpl extends PerunAbstractImpl {

    final static Logger log = LoggerFactory.getLogger(PerunJsonImpl.class);
    static Map<String, Map<String, String>> texty;
    List<File> machineFiles;
    List<File> userFiles;
    Map<File, Long> loadedTimes = new HashMap<>();
    List<VypocetniCentrum> centra;
    Map<String, PerunUser> users;
    List<Stroj> allMachines;
    Map<String, Stroj> allMachinesMap;
    Map<String, VypocetniZdroj> zdrojMap;
    VyhledavacVyhrazenychStroju vyhledavacVyhrazenychStroju;
    VyhledavacFrontendu vyhledavacFrontendu;
    private long lastCheckTime = 0l;
    private String filterVo=null;

    public PerunJsonImpl(List<String> machineFiles, List<String> userFiles) {
        log.debug("init machineFiles={}, userFiles={}", machineFiles, userFiles);
        this.machineFiles = new ArrayList<>(machineFiles.size());
        for (String s : machineFiles) {
            File f = new File(s);
            if (!f.exists()) {
                log.error("file {} does not exist !");
            }
            this.machineFiles.add(f);
        }
        this.userFiles = new ArrayList<>(userFiles.size());
        for (String s : userFiles) {
            File f = new File(s);
            if (!f.exists()) {
                log.error("file {} does not exist !");
            }
            this.userFiles.add(f);
        }
        checkFiles();
    }

    public String getFilterVo() {
        return filterVo;
    }

    public void setFilterVo(String filterVo) {
        this.filterVo = filterVo;
    }

    //tohle je trochu prasárna, ale jak jinak se mám k tomu dostat ze třídy PerunResourceBundle ?
    public static Map<String, Map<String, String>> getTexty() {
        return texty;
    }

    private static String getString(JSONObject jsonObject, String key) {
        JSONValue value = jsonObject.get(key);
        if (value == null || value.isNull()) return "";
        if (value.isString()) {
            JSONString jsonString = (JSONString) value;
            return jsonString.getValue();
        }
        throw new RuntimeException("value for key " + key + " must be string, it is " + value);
    }

    private static int getInt(JSONObject jsonObject, String key) {
        JSONValue value = jsonObject.get(key);
        if (value == null || value.isNull()) return 0;
        if (value.isInteger()) {
            return ((JSONInteger) value).getValue().intValue();
        }
        throw new RuntimeException("value for key " + key + " must be integer, it is " + value);
    }

    private static boolean getBoolean(JSONObject jsonObject, String key) {
        JSONValue value = jsonObject.get(key);
        if (value == null) {
            throw new NullPointerException("cannot load boolean value for key '" + key + "'");
        }
        if (value.isBoolean()) {
            return ((JSONBoolean) value).getValue();
        }
        if (value.isString()) {
            return Boolean.parseBoolean(((JSONString) value).getValue());
        }
        throw new RuntimeException("value for key " + key + " must be boolean or string, i.e. true or \"true\", it is " + value.toString());
    }

    private static void nactiTexty(String pozice, Map<String, Map<String, String>> texty, JSONObject jsonObject, String bundleKey, String jsonKey) {
        JSONValue jsonValue = jsonObject.get(jsonKey);
        if (jsonValue != null && jsonValue.isObject()) {
            JSONObject jtexty = (JSONObject) jsonValue;
            if (log.isDebugEnabled()) log.debug("Loading " + pozice + " jtexty=" + jtexty);
            for (String lang : jtexty.getValue().keySet()) {
                Map<String, String> map = texty.get(lang.trim());
                if (map == null) {
                    map = new HashMap<>(100);
                    texty.put(lang.trim(), map);
                    log.warn("Created text bundle for language '{}'", lang.trim());
                }
                map.put(bundleKey, getString(jtexty, lang));
            }
        } else {
            log.warn("for " + pozice + " value for key " + jsonKey + " is not a map of i18n texts, it is " + (jsonValue == null ? "null" : jsonValue.render(false)));
        }
        if (texty.get("cs").get(bundleKey) == null) texty.get("cs").put(bundleKey, "");
        if (texty.get("en").get(bundleKey) == null) texty.get("en").put(bundleKey, "");
    }

    private static <T> Iterable<T> iterable(final Iterator<T> it) {
        return new Iterable<T>() {
            public Iterator<T> iterator() {
                return it;
            }
        };
    }

    private VypocetniZdroj nactiVypocetniZdroj(Map<String, Map<String, String>> texty, JSONObject jzdroj, List<Stroj> allMachines, HashMap<String, VypocetniZdroj> zdrojMap) {
        String zid = getString(jzdroj, "id");
        boolean clust = getBoolean(jzdroj, "cluster");
        log.debug("loading resource {}", zid);
        VypocetniZdroj zdroj = new VypocetniZdroj(zid, getString(jzdroj, "name"), clust);

        JSONArray vos = (JSONArray) jzdroj.get("vos");
        if(vos!=null)  {
            for(JSONValue jv : vos.getValue()) {
                if(jv.isString()) zdroj.getVoNames().add(((JSONString) jv).getValue());
            }
        }
        if(filterVo!=null&& !zdroj.getVoNames().contains(filterVo)) {
            log.debug("skipping {}, not in vo {}",zid,filterVo);
            return null;
        }

        nactiTexty("resource " + zid, texty, jzdroj, zdroj.getPopisKey(), "desc");
        nactiTexty("resource " + zid, texty, jzdroj, zdroj.getSpecKey(), "spec");

        zdroj.setPhoto(getString(jzdroj, "photo"));
        zdroj.setThumbnail(getString(jzdroj, "thumbnail"));
        zdroj.setCpuDesc(getString(jzdroj, "cpudesc"));
        zdroj.setMemory(getString(jzdroj, "memory"));
        nactiTexty("resource " + zid, texty, jzdroj, zdroj.getDiskKey(), "disk");
        nactiTexty("resource " + zid, texty, jzdroj, zdroj.getNetworkKey(), "network");
        nactiTexty("resource " + zid, texty, jzdroj, zdroj.getCommentKey(), "comment");
        nactiTexty("resource " + zid, texty, jzdroj, zdroj.getOwnerKey(), "owner");



        if (zdroj.isCluster()) {
            zdroj.setStroje(new ArrayList<Stroj>());
            for (JSONValue jv2 : ((JSONArray) jzdroj.get("machines")).getValue()) {
                JSONObject jstroj = (JSONObject) jv2;
                Stroj stroj = new Stroj(zdroj, getString(jstroj, "name"), false, getInt(jstroj, "cpu"));
                zdroj.getStroje().add(stroj);
                allMachines.add(stroj);
            }
        } else {
            Stroj stroj = new Stroj(zdroj, zdroj.getNazev(), false, getInt(jzdroj, "cpu"));
            zdroj.setStroj(stroj);
            zdroj.setStroje(Arrays.asList(stroj));
            allMachines.add(stroj);
        }
        zdrojMap.put(zdroj.getId(), zdroj);
        return zdroj;
    }

    private synchronized void checkFiles() {
        long now = System.currentTimeMillis();
        if (now > lastCheckTime + 60000) {
            log.debug("checking files");
            //musím načíst všechny, protože data se spojují dohromady
            if (modified(this.userFiles)) loadUserFiles();
            if (modified(this.machineFiles)) loadMachineFiles();
            lastCheckTime = now;
        }
    }

    private boolean modified(List<File> files) {
        boolean modified = false;
        for (File f : files) {
            Long time = loadedTimes.get(f);
            if (time == null) time = 0l;
            if (f.lastModified() > time) modified = true;
        }
        return modified;
    }

    private void loadUserFiles() {
        Map<String, PerunUser> users = new HashMap<>(500);
        for (File f : userFiles) {
            log.info("loading {}", f);
            try {
                ObjectMapper mapper = new ObjectMapper();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                for (JsonNode juser : mapper.readValue(f, JsonNode.class).path("users")) {
                    PerunUser perunUser = new PerunUser();
                    perunUser.setLogname(juser.get("logname").textValue());
                    perunUser.setName(juser.get("name").textValue());
                    perunUser.setOrganization(juser.get("org").asText());
                    perunUser.setResearchGroup(juser.get("researchGroup").asText());
                    perunUser.setLang(juser.get("lang").asText());
                    perunUser.setStatus(juser.get("status").asText());
                    perunUser.setMail(juser.get("mail").asText());
                    try {
                        perunUser.setExpires(sdf.parse(juser.path("expires").asText()));
                    } catch (ParseException ex) {
                        perunUser.setExpires(new GregorianCalendar(3000, 0, 1).getTime());
                    }
                    JsonNode jpubls = juser.path("publications");
                    if (jpubls.isObject()) {
                        for (String key : iterable(jpubls.fieldNames())) {
                            perunUser.getPublications().put(key, jpubls.get(key).asInt());
                        }
                    }
                    users.put(perunUser.getLogname(), perunUser);
                }
                loadedTimes.put(f, System.currentTimeMillis());
            } catch (Exception e) {
                log.error("error parsing " + f, e);
            }
        }
        this.users = users;
    }

    private void loadMachineFiles() {
        List<VypocetniCentrum> centra = new ArrayList<>();
        List<Stroj> allMachines = new ArrayList<>(500);
        HashSet<String> reserved_names = new HashSet<>();
        HashSet<String> frontend_names = new HashSet<>();
        HashMap<String, Map<String, String>> texty = new HashMap<>(2);
        HashMap<String, VypocetniZdroj> zdrojMap = new LinkedHashMap<>(40);
        texty.put("cs", new HashMap<String, String>(100));
        texty.put("en", new HashMap<String, String>(100));

        for (File f : machineFiles) {
            log.info("loading {}", f);
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(f), "utf-8"));
                final JSONParser jsonParser = new JSONParser(in);
                JSONObject perun_machines = (JSONObject) jsonParser.nextValue();
                in.close();
                //fyzicke stroje
                JSONArray physical_machines = (JSONArray) perun_machines.get("physical_machines");
                loadVypocetniCentra(centra, texty, physical_machines, allMachines, zdrojMap);
                //vyhrazene
                JSONArray reserved_machines = (JSONArray) perun_machines.get("reserved_machines");
                for (JSONValue jv : reserved_machines.getValue()) {
                    reserved_names.add(((JSONString) jv).getValue());
                }
                //frontendy
                JSONArray frontends = (JSONArray) perun_machines.get("frontends");
                for (JSONValue jv : frontends.getValue()) {
                    frontend_names.add(((JSONString) jv).getValue());
                }
            } catch (Exception e) {
                log.error("error parsing " + f, e);
            }
            loadedTimes.put(f, System.currentTimeMillis());
        }
        //mapa pro rychlejší vyhledávání strojů podle jména
        Map<String, Stroj> allMachinesMap = new HashMap<>(allMachines.size() * 2);
        for (Stroj stroj : allMachines) {
            allMachinesMap.put(stroj.getName(), stroj);
        }
        this.vyhledavacVyhrazenychStroju = new JsonVyhledavacVyhrazenychStroju(reserved_names);
        this.vyhledavacFrontendu = new JsonVyhledavacFrontendu(frontend_names);
        PerunJsonImpl.texty = texty;
        this.centra = centra;
        this.allMachines = allMachines;
        this.allMachinesMap = allMachinesMap;
        this.zdrojMap = zdrojMap;
        PerunResourceBundle.refresh();
    }

    private void loadVypocetniCentra(List<VypocetniCentrum> centra, HashMap<String, Map<String, String>> texty, JSONArray physical_machines, List<Stroj> allMachines, HashMap<String, VypocetniZdroj> zdrojMap) {

        for (JSONValue cv : physical_machines.getValue()) {
            JSONObject jcentrum = (JSONObject) cv;
            String id = getString(jcentrum, "id");
            log.debug("loading centrum {}", id);
            VypocetniCentrum centrum = new VypocetniCentrum(id, "centrum-" + id + "-name", "centrum-" + id + "-url");
            nactiTexty("centrum " + id, texty, jcentrum, centrum.getNazevKey(), "name");
            nactiTexty("centrum " + id, texty, jcentrum, centrum.getUrlKey(), "url");
            nactiTexty("centrum " + id, texty, jcentrum, centrum.getSpecKey(), "spec");

            centrum.setZdroje(new ArrayList<VypocetniZdroj>());
            for (JSONValue jv : ((JSONArray) jcentrum.get("resources")).getValue()) {
                JSONObject jzdroj = (JSONObject) jv;
                VypocetniZdroj zdroj = nactiVypocetniZdroj(texty, jzdroj, allMachines, zdrojMap);
                if(zdroj!=null) {
                    centrum.getZdroje().add(zdroj);
                    if (zdroj.isCluster()) {
                        zdroj.setPodclustery(new ArrayList<VypocetniZdroj>());
                        JSONValue subclusters = jzdroj.get("subclusters");
                        if (subclusters != null) {
                            for (JSONValue jv3 : ((JSONArray) subclusters).getValue()) {
                                JSONObject jsubcluster = (JSONObject) jv3;
                                zdroj.getPodclustery().add(nactiVypocetniZdroj(texty, jsubcluster, allMachines, zdrojMap));
                            }
                        }
                    }
                }
            }
            if(!centrum.getZdroje().isEmpty()) {
                centra.add(centrum);
            }
        }
    }

    @Override
    public Map<String, String> nactiVsechnyTexty(Locale locale) {
        return texty.get(locale.toString());
    }

    @Override
    public List<VypocetniCentrum> najdiVypocetniCentra() {
        checkFiles();
        return centra;
    }

    @Override
    public List<Stroj> getMetacentroveStroje() {
        return allMachines;
    }

    @Override
    public PerunUser getUserByName(String userName) {
        return users.get(userName);
    }

    @Override
    public boolean isNodeVirtual(String nodeName) {
        boolean b = !isNodePhysical(nodeName);
        log.debug("isNodeVirtual({}) returns {}",nodeName,b);
        return b;
    }

    @Override
    public boolean isNodePhysical(String nodeName) {
        Stroj stroj = getStrojByName(nodeName);
        boolean b = stroj != null && !stroj.isVirtual();
        log.debug("isNodePhysical({}) returns {}",nodeName,b);
        return b;
    }

    @Override
    public Stroj getStrojByName(String machineName) {
        Stroj stroj = allMachinesMap.get(machineName);
        log.debug("getStrojByName({}) returns {}",machineName,stroj);
        return stroj;
    }

    @Override
    public VypocetniZdroj getVypocetniZdrojByName(String zdrojName) {
        return zdrojMap.get(zdrojName);
    }

    /**
     * Vrací třídu, která rozhoduje, zda má být daný stroj označen jako modrý protože je mimo PBS záměrně.
     *
     * @return vyhledavac
     */
    @Override
    public VyhledavacVyhrazenychStroju getVyhledavacVyhrazenychStroju() {
        return vyhledavacVyhrazenychStroju;
    }

    @Override
    public VyhledavacFrontendu getVyhledavacFrontendu() {
        return vyhledavacFrontendu;
    }

    @Override
    public List<PerunUser> getAllUsers() {
        return new ArrayList<>(users.values());
    }

    private static class JsonVyhledavacVyhrazenychStroju extends VyhledavacJmen implements VyhledavacVyhrazenychStroju {
        public JsonVyhledavacVyhrazenychStroju(Set<String> mnozinaJmen) {
            super(mnozinaJmen);
        }

        @Override
        public boolean jeStrojVyhrazeny(Stroj stroj) {
            return this.jeTam(stroj.getName());
        }
    }

    private static class JsonVyhledavacFrontendu extends VyhledavacJmen implements VyhledavacFrontendu {
        public JsonVyhledavacFrontendu(Set<String> mnozinaJmen) {
            super(mnozinaJmen);
        }

        @Override
        public boolean jeStrojFrontend(String dlouheJmeno) {
            return this.jeTam(dlouheJmeno);
        }
    }
}
