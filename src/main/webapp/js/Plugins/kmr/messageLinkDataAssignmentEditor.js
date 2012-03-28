if (!ORYX.Plugins) 
    ORYX.Plugins = new Object();


   
ORYX.Plugins.MessageLinkDataAssignmentEditor = Clazz.extend({
    facade: undefined,
    
    construct: function(facade){
        this.facade = facade;
        
        ORYX.AssociationEditors["MessageLink"] = new ORYX.Plugins.MessageLinkDataAssignmentEditor.MessageLinkDataAssignmentEditorFactory();
        
    }

});

ORYX.Plugins.MessageLinkDataAssignmentEditor.MessageLinkDataAssignmentEditorFactory = Clazz.extend({
    construct: function(){
        
    },
    
    init: function(){
        var grid = arguments[0];
        var record = arguments[1];
        var colIndex = arguments[2];
        var rowIndex = arguments[3];
        
        var messageLinkEditor = new Ext.form.MessageLinkEditor({
            grid: grid, 
            record: record, 
            colIndex: colIndex, 
            rowIndex: rowIndex
        });
        
        var ed = new Ext.Editor(messageLinkEditor);

        //update the value in the grid
        messageLinkEditor.on("editioncomplete", function(newValue, oldValue){
            grid.onEditComplete(ed, newValue, oldValue);
        })
        
        return ed;
    }
});

Ext.form.MessageLinkEditor = Ext.extend(Ext.form.TriggerField,{
    
    initComponent : function(){
        Ext.form.MessageLinkEditor.superclass.initComponent.call(this);

        this.addEvents(
            "editioncomplete"
            );
    },
    
    onTriggerClick : function() {
        if(this.disabled){
            return;
        }
    	
        var CommunicationLinkDef = Ext.data.Record.create([
        {
            name: 'receiver'
        },
        {
            name: 'channel'
        },
        {
            name: 'template'
        },
        {
            name: 'timeout'
        }
        ]);
    	
        var communicationLinksProxy = undefined;
        
        if (this.value && this.value != ""){
            var realValue = this.value.replace(/_U_U_/gi, ",");
            communicationLinksProxy = new Ext.data.MemoryProxy({
                root: realValue.evalJSON(true)
            });
        } else{
            communicationLinksProxy = new Ext.data.MemoryProxy({
                root: []
            });
        }
    	
        var communicationLinks = new Ext.data.Store({
            autoDestroy: true,
            reader: new Ext.data.JsonReader({
                root: "root"
            }, CommunicationLinkDef),
            proxy: communicationLinksProxy
        });
        communicationLinks.load();
    	
        var itemDeleter = new Extensive.grid.ItemDeleter();
    	
        var gridId = Ext.id();
        var grid = new Ext.grid.EditorGridPanel({
            store: communicationLinks,
            id: gridId,
            stripeRows: true,
            cm: new Ext.grid.ColumnModel([new Ext.grid.RowNumberer(), 
            {
                id: 'receiver',
                header: 'Receiver',
                dataIndex: 'receiver',
                editor: new Ext.form.TextField({
                    allowBlank: false
                })
            },
            {
                id: 'channel',
                header: 'Channel',
                dataIndex: 'channel',
                editor: new Ext.form.TextField({
                    allowBlank: false
                })
            },
            {
                id: 'template',
                header: 'Template',
                dataIndex: 'template',
                editor: new Ext.form.TextField({
                    allowBlank: false
                })
            },
            {
                id: 'timeout',
                header: 'Timeout',
                dataIndex: 'timeout',
                editor: new Ext.form.TextField({
                    allowBlank: false
                })
            },
            itemDeleter]
            ),
            selModel: itemDeleter,
            autoHeight: true,
            tbar: [{
                text: 'Add Entry',
                handler : function(){
                    communicationLinks.add(new CommunicationLinkDef({
                        receiver: '',
                        channel: '',
                        template: '',
                        timeout: ''
                    }));
                    grid.fireEvent('cellclick', grid, communicationLinks.getCount()-1, 1, null);
                }
            }],
            clicksToEdit: 1
        });
    	
        var dialog = new Ext.Window({ 
            layout		: 'anchor',
            autoCreate	: true, 
            title		: 'Editor for Communication Configuration', 
            height		: 300, 
            width		: 480, 
            modal		: true,
            collapsible	: false,
            fixedcenter	: true, 
            shadow		: true, 
            resizable   : true,
            proxyDrag	: true,
            autoScroll  : true,
            keys:[{
                key	: 27,
                fn	: function(){
                    dialog.hide()
                }.bind(this)
            }],
            items		:[grid],
            listeners	:{
                hide: function(){
                    this.fireEvent('dialogClosed', this.value);
                    //this.focus.defer(10, this);
                    dialog.destroy();
                }.bind(this)				
            },
            buttons		: [{
                text: ORYX.I18N.PropertyWindow.ok,
                handler: function(){	 
                    grid.stopEditing();
                    grid.getView().refresh();
                        
                    var oldValue = this.getValue();
                    
                    
                    var values = communicationLinks.getRange();
                    
                    var newValue = [];
                    values.each(function(record){
                        newValue.push(record.data);
                    });
                    
                    newValue = Object.toJSON(newValue); 
                    
                    //association editor will separate values using ',' as
                    //separator. We can't use ',' then.
                    newValue = newValue.replace(/,/gi, "_U_U_");
                    
                    this.setValue(newValue);
                    this.fireEvent("editioncomplete", newValue, oldValue);
                    dialog.close();
                }.bind(this)
            }, {
                text: ORYX.I18N.PropertyWindow.cancel,
                handler: function(){
                    this.setValue(this.value);
                    dialog.hide()
                }.bind(this)
            }]
        });		
				
        dialog.show();		
        grid.render();

        this.grid.stopEditing();
        grid.focus( false, 100 );
    	
    }
    /*
    onTriggerClick2 : function(){
        var cf = new Ext.form.TextArea({
            value: this.value
        });

        var win = new Ext.Window({
            width:400,
            height:450,
            layout: 'fit',
            title:'Message Link Definition',
            items: [cf],
            buttons		: [
            {
                text: 'Ok',
                handler: function(){
                    var oldValue = this.getValue();
                    var newValue = cf.getValue();
                        
                    //minimum check -> the value must be valid JSON
                    try{
                        newValue.evalJSON(true);
                    }catch(e){
                        alert (e);
                        return;
                    }
                        
                    this.setValue(newValue);
                    this.fireEvent("editioncomplete", newValue, oldValue);
                    win.close();
                }.bind(this)
            },
            {
                text: 'Cancel',
                handler: function(){
                    win.close();
                }
            }]
        });
        win.show();
        cf.focus(false, 100);
    }
    */
    
});