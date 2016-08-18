<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <!-- A style sheet that can be applied to entities.xml from a Confluence dump
       and produces just the bare bones data about the hierarchy of pages in (the|each) space,
       in the order they appear as children of their parent
  -->
  
  <xsl:output indent="yes"/>
  
  <xsl:template match="/">
    <confluence>
      <xsl:apply-templates select="//object[@class='Space']"/>
    </confluence>
  </xsl:template>
  
  <xsl:template match="object[@class='Space']">
    <space>
      <xsl:attribute name="id"><xsl:value-of select="./id/text()"/></xsl:attribute>
      <xsl:attribute name="name"><xsl:value-of select="./property[@name='name']/text()"/></xsl:attribute>

      <!-- can't just look for "pages that have no parent" because that will also match old versions.
           (even the historical versions of pages have a status of 'current')
      -->
      <!--
          So instead look for any page that is part of the space, and does have some position
          (sort order in space), but does not have a parent
      -->
      <xsl:apply-templates select="//object[@class='Page'][boolean(property[@name='position']/text())][not(property[@name='parent'])][property[@name='space']/id/text()=current()/id/text()]" >
        <!-- NOTE: sort duplicated in recursive Page template below -->
        <xsl:sort data-type="number" order="ascending"
                  select="property[@name='position']/text()" />
        <!-- aparently pages only have position if user has explicitly sorted?
             otherwise it looks like they default to sort by title? -->
        <xsl:sort data-type="text" order="ascending"
                  select="property[@name='title']/text()" />
      </xsl:apply-templates>
    </space>
  </xsl:template>

  <!-- NOTE: This template is recursive -->
  <xsl:template match="object[@class='Page']">
    <page>
      <xsl:attribute name="id"><xsl:value-of select="./id/text()"/></xsl:attribute>
      <xsl:attribute name="title"><xsl:value-of select="./property[@name='title']/text()"/></xsl:attribute>
      <!-- add parent info redundently in case it's helpful -->
      <xsl:if test="./property[@name='parent']/id">
        <xsl:attribute name="parent"><xsl:value-of select="./property[@name='parent']/id/text()"/></xsl:attribute>
      </xsl:if>

      <!-- the sort order if explicitly set by a confluence user at some point
           If this has never been set for a group of children, it aparently defaults to
           sorting all those children by alpha page title
      -->
      <xsl:if test="./property[@name='position']/node()">
        <xsl:attribute name="sort"><xsl:value-of select="./property[@name='position']/text()"/></xsl:attribute>
      </xsl:if>
      
      <!-- NOTE: doing a for-each on collection[@name='children'] won't work....
           collection isn't sorted, need to use "position" property from the Pages themselves
           
           <xsl:for-each select="collection[@name='children']/element[@class='Page']/id/text()">
           <xsl:apply-templates select="//object[@class='Page'][id/text()=current()]"/>
           </xsl:for-each>
      -->
      
      <!-- instead we go out and select every page that has a parent which matches our id
           (thank god for the parent property) and (recursively) apply templates in "position" sorted order
      -->
      <xsl:apply-templates select="//object[@class='Page'][property[@name='parent']/id/text()=current()/id/text()]">
        <!-- NOTE: sort duplicated in Space template above -->
        <xsl:sort data-type="number" order="ascending"
                  select="property[@name='position']/text()" />
        <!-- aparently pages only have position if user has explicitly sorted?
             otherwise it looks like they default to sort by title? -->
        <xsl:sort data-type="text" order="ascending"
                  select="property[@name='title']/text()" />
      </xsl:apply-templates>
    </page>
  </xsl:template>
  <xsl:template match="object" /><!-- No-Op for other types of objects -->
</xsl:stylesheet>
