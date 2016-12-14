<%@ page pageEncoding="utf-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="f" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="s" uri="http://stripes.sourceforge.net/stripes.tld" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="h" uri="http://stripes.sourceforge.net/stripes.tld" %>


<s:useActionBean beanclass="cz.cesnet.meta.stripes.CloudActionBean" var="actionBean"/>
<f:message var="titlestring" key="cloud.titul" scope="request"/>
<s:layout-render name="/layout.jsp">
    <s:layout-component name="hlava">
        <style type="text/css">
            div.cloud-phys {
                border: 1px solid gray;
                padding: 0;
                margin: 5px;
                width: 80%;
            }

            div.cloud-phys-title {
                color: white;
                font-weight: bold;
                background-color: gray;
                padding: 3px;;
            }

            td.cloud-virt {
                font-weight: bold;
            }

            td.ACTIVE {
                background-color: #6060FF;
            }

            td.POWEROFF, td.SUSPENDED {
                background-color: #C0C0C0;
            }

        </style>
    </s:layout-component>
    <s:layout-component name="telo">

        <p>
            <c:choose>
                <c:when test="${pageContext.request.locale=='cs'}">
                    <a href="http://meta.cesnet.cz/wiki/Kategorie:Cloudy">Dokumentace ke cloudu v MetaCentru</a>
                </c:when>
                <c:otherwise>
                    <a href="http://meta.cesnet.cz/wiki/Kategorie:Clouds">Documentation for cloud in MetaCentrum</a>
                </c:otherwise>
            </c:choose>
        </p>

        <p><f:message key="cloud_jsp_celkem_cpu"/>: ${actionBean.cpuMap['vsechny']}</p>

        <h2><f:message key="cloud_jsp_headline_physical_machines"/></h2>

        <p><f:message key="cloud_jsp_barvy"/> :</p>
        <table>
            <tr>
                <td class="free">
                    <div style="background: url('${pageContext.request.contextPath}/img/cloud.png') top right no-repeat; text-align: center;">
                        <f:message key="nodesjsp_state_free"/>&nbsp;&nbsp;&nbsp;&nbsp;</div>
                </td>
            </tr>
            <tr>
                <td class="partialy-free" style="position: relative;">
                    <div style="position: absolute; bottom: 0; left: 0; background-color: #6060FF; width: 100%; height: 50%; z-index: 1"></div>
                    <div style="z-index: 10; position: relative; background: url('${pageContext.request.contextPath}/img/cloud.png') top right no-repeat; text-align: center;">
                        <f:message key="nodesjsp_state_partialy-free"/></div>
                </td>
            </tr>
            <tr>
                <td class="job-busy">
                    <div style="background: url('${pageContext.request.contextPath}/img/cloud.png') top right no-repeat; text-align: center;">
                        <f:message key="cloud_state_blue"/>&nbsp;&nbsp;&nbsp;&nbsp;</div>
                </td>
            </tr>
            <tr>
                <td class="nebulapbshost">
                    <div style="background: url('${pageContext.request.contextPath}/img/cloud.png') top right no-repeat; text-align: center;">
                        <f:message key="cloud_state_nebulapbshost_partial"/>&nbsp;&nbsp;&nbsp;&nbsp;</div>
                </td>
            </tr>
            <tr>
                <td class="nebulapbshost">
                    <div style="text-align: center;"><f:message key="cloud_state_nebulapbshost_full"/>&nbsp;&nbsp;&nbsp;&nbsp;</div>
                </td>
            </tr>

        </table>

        <c:forEach items="${actionBean.centra}" var="centrum">
            <c:forEach var="zdr" items="${centrum.zdroje}" varStatus="s">
                <c:choose>
                    <c:when test="${actionBean.inCloudMap[zdr.id]}">
                        <table width="90%">
                            <c:if test="${!s.first}">
                                <tr>
                                    <td colspan="2">&nbsp;</td>
                                </tr>
                            </c:if>
                            <tr>
                                <td colspan="2"><a name="${zdr.id}"></a>
                                    <s:link href="/resource/${zdr.id}"><c:out value="${zdr.nazev}"/></s:link>
                                    <c:if test="${zdr.cluster}">(${actionBean.cpuMap[zdr.id]} CPU)</c:if>
                                    - <f:message key="${zdr.popisKey}"/></td>
                            </tr>
                            <f:message key="${zdr.specKey}" var="chunk"/>
                            <c:if test="${! empty chunk}">
                                <tr>
                                    <td colspan="2" style="padding-left: 20px; font-size: x-small;"><f:message
                                            key="${zdr.specKey}"/></td>
                                </tr>
                            </c:if>
                            <tr>
                                <td colspan="2" style="padding-left: 20px;">
                                    <c:choose>
                                        <c:when test="${zdr.cluster}">
                                            <c:if test="${! empty zdr.stroje}">
                                                <table class="nodes" cellspacing="0">
                                                    <tr><c:set var="ci" value="${0}"/>
                                                        <c:forEach items="${zdr.stroje}" var="stroj" varStatus="i">
                                                        <c:choose>
                                                            <c:when test="${stroj.nebulaPbsHost}">
                                                                <c:set var="ci" value="${ci+1}"/>
                                                                <td class="node nebulapbshost <c:if test='${stroj.openNebulaUsable}'>opennebulausable</c:if>"
                                                                    style="height: ${stroj.cpuNum/8}em; padding: 0;">
                                                                    <s:link style="line-height: ${stroj.cpuNum/8}em;"
                                                                            href="/machine/${stroj.name}"><c:out
                                                                            value="${stroj.pbsName}"/>&nbsp;(${stroj.cpuNum}&nbsp;CPU)</s:link>
                                                                </td>
                                                            </c:when>
                                                            <c:when test="${stroj.openNebulaManaged}">
                                                                <c:set var="ci" value="${ci+1}"/>
                                                                <t:stroj stroj="${stroj}"/>
                                                            </c:when>
                                                            <c:otherwise>
                                                                <!-- stroj ${stroj.name} neni v cloudu -->
                                                            </c:otherwise>
                                                        </c:choose>
                                                        <c:if test="${ci%8==0}"></tr>
                                                    <tr></c:if>
                                                        </c:forEach>
                                                    </tr>
                                                </table>
                                            </c:if>
                                        </c:when>
                                        <c:otherwise>
                                            <table class="nodes" cellspacing="0">
                                                <tr><t:stroj stroj="${zdr.stroj}"/></tr>
                                            </table>
                                        </c:otherwise>
                                    </c:choose>
                                </td>
                            </tr>
                        </table>
                    </c:when>
                    <c:otherwise>
                        <!-- zdroj ${zdr.id} neni v cloudu -->
                    </c:otherwise>
                </c:choose>
            </c:forEach>
        </c:forEach>


        <!-- cloud only -->
        <h2><f:message key="cloud_jsp_headline_vm_assignment"/></h2>

        <c:forEach items="${actionBean.physicalHosts}" var="ph">
            <div class="cloud-phys">
                <div class="cloud-phys-title"><c:out value="${ph.hostname}"/> (<c:out value="${ph.cpuAvail}"/> CPU) -
                    reserved <c:out value="${ph.cpuReserved}"/> CPU, state <c:out value="${ph.state}"/></div>
                <div class="cloud-phys-body">
                    <table class="cloud-virts">
                        <c:forEach items="${actionBean.vms[ph.name]}" var="vm">
                            <tr class="cloud-virt-line">
                                <c:choose>
                                    <c:when test="${not empty vm.node}">
                                        <td class="node ${vm.node.state}"><s:link class="${vm.node.state}"
                                                                                  href='/node/${vm.node.name}'>${vm.node.shortName}
                                            (<c:out value="${vm.cpuReservedString}"/> CPU<c:if
                                                    test="${vm.cpu_reserved_x100!=vm.cpu_avail_x100}">/${vm.VCPU}VCPU</c:if>)</s:link></td>
                                        <td><c:choose><c:when
                                                test="${vm.role=='pbs_mom'}">PBS node</c:when><c:otherwise>role: ${vm.role}</c:otherwise></c:choose></td>
                                    </c:when>
                                    <c:otherwise>
                                        <td class="cloud-virt ${vm.state}"><c:out value="${vm.fqdn}"/> (<c:out
                                                value="${vm.cpuReservedString}"/> CPU<c:if
                                                test="${vm.cpu_reserved_x100!=vm.cpu_avail_x100}">/${vm.VCPU}VCPU</c:if>)
                                        </td>
                                        <td>Cloud node "<c:out value="${vm.name}"/>" of user <t:vmowner
                                                owner="${vm.owner}"/>
                                            <c:choose>
                                                <c:when test="${vm.state=='ACTIVE'}">
                                                    started <f:formatDate value="${vm.startTime}"
                                                                          pattern="yyyy-MM-dd HH:mm:ss"/>
                                                </c:when>
                                                <c:otherwise>
                                                    in state ${vm.state}
                                                </c:otherwise>
                                            </c:choose>
                                        </td>
                                    </c:otherwise>
                                </c:choose>
                            </tr>
                        </c:forEach>
                    </table>
                </div>
            </div>

        </c:forEach>
    </s:layout-component>
</s:layout-render>
