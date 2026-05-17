with open('backend/routers/community.py', 'r', encoding='utf-8') as f:
    content = f.read()

new_code = '''    db.refresh(new_post)
    return new_post

@router.put("/posts/{post_id}")
def update_community_post(
    post_id: int, 
    post_data: CommunityPostCreate, 
    db: Session = Depends(get_db_session)
):
    post = db.query(models.CommunityPost).filter(models.CommunityPost.id == post_id).first()
    if not post:
        raise HTTPException(status_code=404, detail="Post not found")
        
    if post.user_id != post_data.user_id:
        raise HTTPException(status_code=403, detail="Only the post creator can edit")
        
    post.title = post_data.title
    post.content = post_data.content
    post.target_price = post_data.target_price
    post.bounty_points = post_data.bounty_points
    post.location = post_data.location
    post.post_type = post_data.post_type
    
    db.commit()
    db.refresh(post)
    return post
'''

content = content.replace('    db.refresh(new_post)\n    return new_post', new_code)

with open('backend/routers/community.py', 'w', encoding='utf-8') as f:
    f.write(content)
