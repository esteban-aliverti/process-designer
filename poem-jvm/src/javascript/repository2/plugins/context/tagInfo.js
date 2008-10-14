/**
 * Copyright (c) 2008
 * Willi Tscheschner
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 **/

// define namespace
if(!Repository) var Repository = {};
if(!Repository.Plugins) Repository.Plugins = {};

/**
 * Supplies filtering by model type (stencil set)
 * Note: Only stencil sets defined in the stencilsets.json can be selected as filter
 */

Repository.Plugins.TagInfo = {
	
	TAG_URL : "/tags",
	
	construct: function( facade ) {
		// Set the name
		this.name = Repository.I18N.TagInfo.name;

		this.dataUris = [this.TAG_URL];
										
		// call Plugin super class
		arguments.callee.$.construct.apply(this, arguments); 
		
		this._generateGUI();

	},
	
	render: function( modelData ){

		if( !this.tagPanel ){ return }

		// Try to removes the old child ...
		if( this.tagPanelContent )
			this.tagPanel.remove( this.tagPanelContent );
			
					
		var oneIsSelected 	= $H(modelData).keys().length !== 0;
		var isPublic		= this.facade.isPublicUser();
		var buttons 		= [];
		
		// Find every tag which are available in all selected models
		var modelTags 		= []
		$H(modelData).each(function( pair ){ 
						pair.value.userTags.each(function( tag ){
							if( modelData.every(function( spair ){
									return spair.value.userTags.include( tag )
								}) ){
								modelTags.push( unescape( tag ) )
							} 
						})
					})
		
		modelTags = modelTags.uniq();
		
		// For each modeltag create a label						
		modelTags.each(function(tag, index){
			
			var label = {text: tag, xtype:'label'};
			var image = new Ext.LinkButton({image:'../images/silk/cross.png', imageStyle:'width:12px; margin:0px 2px -2px 2px;', text:Repository.I18N.TagInfo.deleteText, click:this._onTagClick.bind(this, tag)})
			
			buttons.push( label );
			if (!isPublic) buttons.push( image ); // Don't display remove buttons to the public user
			
			if( index < modelTags.length-1 )
				buttons.push( {html:', ', width:10, xtype:'label'} );
				
		}.bind(this))

		if( buttons.length == 0 ){
			// Add a 'none'
			buttons.push( {text: Repository.I18N.TagInfo.none, xtype:'label', style:"font-style:italic;color:gray;"} );				
		}
	
		
		this.tagPanelContent = new Ext.Panel({
									items	: buttons,
									border	: false
								})			

		if( this.controls ){
			this.controls.each(function(co){
				co.setDisabled( isPublic || !oneIsSelected )
			}.bind(this))
			
			this.controls[0].setValue("")
		}

		this.tagPanel.add( this.tagPanelContent );
		this.tagPanel.doLayout();

	},
	
	
	_generateGUI: function(){

		var label 		= {text: Repository.I18N.TagInfo.shared, xtype:'label', style:"display:block;font-weight:bold;margin-bottom:5px;"};
		this.tagPanel	= new Ext.Panel({border:false})
		this.controls	= [
								new Ext.form.TextField({
											id		: 'repository_taginfo_textfield',
											x		: 0, 
											y		: 0, 
											width	: 100,
											emptyText : Repository.I18N.TagInfo.newTag ,
											disabled  : true,  
										}),
								 new Ext.Button({
											text 		: Repository.I18N.TagInfo.addTag,
											disabled 	: true, 
											listeners	: {
												click : function(){
													this._addTag(Ext.getCmp('repository_taginfo_textfield').getValue())
												}.bind(this)
											}
										})
							]
							
		// Generate a new panel for the add form
		var addPanel = new Ext.Panel({
					style	: 'padding-top:10px;',
					layout	: 'absolute',
					border	: false,
					height	: 40,
					items	: [
								this.controls[0],
								new Ext.Panel({
											x		: 105, 
											y		: 0,
											border	: false,
											items	: [this.controls[1]]
										})
								]
				});

		var isPublicUser	= this.facade.isPublicUser();
		
		var panels	= [label, this.tagPanel]
		if( !isPublicUser ){
			panels.push( addPanel )
		}
		
		this.myPanel = new Ext.Panel({
					style	: 'padding:10px;', 
					border	: false,
					items	: panels
				})
						
		// ... before the new child gets added		
		this.panel.add( this.myPanel );
		// Update layouting
		this.panel.doLayout();
				
	},
	
	_onTagClick: function( tag ){
		
		if( !tag || tag.length <= 0 ){ return }
		
		tag = escape( tag )
		
		this.facade.modelCache.deleteData( this.facade.getSelectedModels(), this.TAG_URL, {tag_name:tag} )
	},	
	
	_addTag: function( tagname ){
		
		if( !tagname || tagname.length <= 0 ){ return }
		
		tagname = escape( tagname )
		
		this.facade.modelCache.setData( this.facade.getSelectedModels(), this.TAG_URL, {tag_name:tagname} )
		
	}
};

Repository.Plugins.TagInfo = Repository.Core.ContextPlugin.extend(Repository.Plugins.TagInfo);