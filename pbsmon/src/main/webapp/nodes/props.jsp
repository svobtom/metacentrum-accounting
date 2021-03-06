<%@ page pageEncoding="utf-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="f" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="s" uri="http://stripes.sourceforge.net/stripes.tld" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="h" uri="http://stripes.sourceforge.net/stripes.tld" %>

<s:useActionBean beanclass="cz.cesnet.meta.stripes.PropsActionBean" var="actionBean"/>
<f:message var="titlestring" key="props.titul" scope="request"/>
<s:layout-render name="/layout.jsp">
    <s:layout-component name="telo">

<ul>
 <li><a href="#node2prop"><f:message key="props_node2prop"/></a></li>
 <li><a href="#prop2node"><f:message key="props_prop2node"/></a></li>
 <li><a href="#group2node"><f:message key="props_group2node"/></a></li>
</ul>


<a name="node2prop"><h3><f:message key="props_node2prop"/></h3></a>
<table class="node2prop">
 <tr>
  <th><f:message key="props_node2prop_node"/></th>
  <th><f:message key="props_node2prop_prop"/></th>
 </tr>
 <c:forEach items="${actionBean.nodes}" var="n">
 <tr>
  <td class="${n.state}"><h:link class="${n.state}" href="/node/${n.name}">${n.name}</h:link>
  <td><c:forEach items="${n.properties}" var="property"><c:out value="${property}"/> </c:forEach></td>
 </c:forEach>
</table>

<a name="prop2node"><h3><f:message key="props_prop2node"/></h3></a>
 <c:forEach items="${actionBean.props}" var="p">
   <br><s:link href="/props"><s:param name="property" value="${p}"/><c:out value="${p}"/></s:link>
 </c:forEach>

<a name="group2node"><h3><f:message key="props_group2node"/></h3></a>
<table>
 <c:forEach items="${actionBean.propsGroupMap}" var="host">
 <tr><td>${host.key}</td></tr>
 <tr>
  <td>
  <c:set var="numinrow" value="${0}"/>
  <table class="nodes" cellspacing="0" border="0">
  <tr>
  <c:forEach items="${host.value}" var="node">
     <c:if test="${numinrow==8}">
         <c:set var="numinrow" value="${0}"/>
         </tr><tr>
     </c:if>
     <td class="${node.state}"> <h:link class="${node.state}" href="/node/${node.name}">${node.shortName}</h:link></td>
     <c:set var="numinrow" value="${numinrow+1}"/>
 </c:forEach>
 </tr>
 </table>
 </td></tr>
 <tr><td><hr/></td></tr>    
 </c:forEach>
</table>


  </s:layout-component>
</s:layout-render>
